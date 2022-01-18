package com.markosindustries.distroboy.kafka.archiver;

import static java.util.UUID.randomUUID;
import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.CLIENT_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.FETCH_MIN_BYTES_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.ISOLATION_LEVEL_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.markosindustries.distroboy.core.Cluster;
import com.markosindustries.distroboy.core.Logging;
import com.markosindustries.distroboy.core.clustering.serialisation.Serialisers;
import com.markosindustries.distroboy.core.iterators.IteratorWithResources;
import com.markosindustries.distroboy.core.operations.DistributedOpSequence;
import com.markosindustries.distroboy.kafka.KafkaTopicPartitionsIterator;
import com.markosindustries.distroboy.kafka.KafkaTopicPartitionsSource;
import com.markosindustries.distroboy.kafka.TimestampKafkaOffsetSpec;
import com.markosindustries.distroboy.parquet.ParquetAvroFilesWriterStrategy;
import com.markosindustries.distroboy.parquet.WriteToParquet;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.aeonbits.owner.ConfigFactory;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.IsolationLevel;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

public class Main {
  private static final Logger log = LoggerFactory.getLogger(Main.class);

  private static final Path tmpParquetLocation = Path.of("/tmp/archiver");

  public static void main(String[] args) throws Exception {
    Logging.configureDefault();

    final var distroBoyConfig = ConfigFactory.create(DistroBoyConfig.class);
    final var kafkaConfig = ConfigFactory.create(KafkaConfig.class);
    final var s3Config = ConfigFactory.create(S3Config.class);
    final var archiverConfig = ConfigFactory.create(ArchiverConfig.class);
    try (final var cluster =
        Cluster.newBuilder("distroboy-kafka-archiver", distroBoyConfig.expectedMembers())
            .coordinator(distroBoyConfig.coordinatorHost(), distroBoyConfig.coordinatorPort())
            .memberPort(distroBoyConfig.memberPort())
            .join()) {

      final var kafkaConsumer = buildKafkaConsumer(kafkaConfig);
      final var s3Client = buildS3Client(s3Config);

      cluster
          .execute(
              DistributedOpSequence.readFrom(
                      new KafkaTopicPartitionsSource(kafkaConsumer, archiverConfig.topics()))
                  .flatMap(
                      topicPartitions ->
                          IteratorWithResources.from(
                              new KafkaTopicPartitionsIterator<>(
                                  kafkaConsumer,
                                  topicPartitions,
                                  new TimestampKafkaOffsetSpec(archiverConfig.startTimestampMs()),
                                  new TimestampKafkaOffsetSpec(archiverConfig.endTimestampMs()))))
                  .map(ArchiveRecord::new)
                  .reduce(
                      new WriteToParquet<ArchiveRecord, Path>(
                          new ParquetAvroFilesWriterStrategy<>(
                              record -> {
                                final var startOfDayUtc =
                                    Math.floorDiv(record.timestamp, 86400000) * 86400000;
                                try {
                                  final var path =
                                      tmpParquetLocation
                                          .resolve(record.topic)
                                          .resolve(Integer.toString(record.partition))
                                          .resolve(Long.toString(startOfDayUtc));
                                  Files.createDirectories(path);
                                  return path.resolve(cluster.clusterMemberId + ".parquet");
                                } catch (IOException e) {
                                  throw new RuntimeException(e);
                                }
                              },
                              ArchiveRecord.class)))
                  .flatMap(IteratorWithResources::from)
                  .map(
                      path -> {
                        final var key = tmpParquetLocation.relativize(path).toString();
                        s3Client.putObject(req -> req.bucket(s3Config.bucketName()).key(key), path);
                        return key;
                      })
                  .map(Object::toString)
                  .collect(Serialisers.stringValues))
          .onClusterLeader(
              s3Keys -> {
                log.info("Wrote events to S3 keys: {}", s3Keys);
              });
    }
  }

  static KafkaConsumer<byte[], byte[]> buildKafkaConsumer(KafkaConfig kafkaConfig) {
    final var kafkaConfigMap =
        ImmutableMap.<String, Object>builder()
            .put(BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.kafkaBrokers())
            .put(CLIENT_ID_CONFIG, "distroboy-kafka-archiver" + randomUUID())
            .put(ENABLE_AUTO_COMMIT_CONFIG, false)
            .put(MAX_POLL_RECORDS_CONFIG, 1000)
            .put(SESSION_TIMEOUT_MS_CONFIG, 10 * 1000)
            .put(HEARTBEAT_INTERVAL_MS_CONFIG, 3 * 1000)
            .put(KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class)
            .put(VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class)
            .put(FETCH_MIN_BYTES_CONFIG, 1)
            .put(FETCH_MAX_WAIT_MS_CONFIG, 500)
            .put(
                ISOLATION_LEVEL_CONFIG,
                IsolationLevel.READ_COMMITTED.toString().toLowerCase(Locale.ROOT))
            .build();

    return new KafkaConsumer<byte[], byte[]>(kafkaConfigMap);
  }

  static S3Client buildS3Client(S3Config s3Config) throws URISyntaxException {
    final var s3ClientBuilder = S3Client.builder();
    if (!Strings.isNullOrEmpty(s3Config.s3EndpointOverride())) {
      s3ClientBuilder.endpointOverride(new URI(s3Config.s3EndpointOverride()));
    }
    if (s3Config.useLocalStackForS3()) {
      s3ClientBuilder.credentialsProvider(AnonymousCredentialsProvider.create());
    }
    return s3ClientBuilder.region(Region.of(s3Config.awsRegion())).build();
  }
}
