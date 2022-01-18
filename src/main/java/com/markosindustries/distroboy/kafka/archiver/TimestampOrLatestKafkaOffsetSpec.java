package com.markosindustries.distroboy.kafka.archiver;

import static java.util.stream.Collectors.toMap;

import com.markosindustries.distroboy.kafka.KafkaOffsetSpec;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;

public class TimestampOrLatestKafkaOffsetSpec implements KafkaOffsetSpec {

  private final long timestampMs;

  public TimestampOrLatestKafkaOffsetSpec(Instant instant) {
    this(instant.toEpochMilli());
  }

  public TimestampOrLatestKafkaOffsetSpec(long timestampMs) {
    this.timestampMs = timestampMs;
  }

  @Override
  public <K, V> Map<TopicPartition, Long> getOffsets(
      Consumer<K, V> kafkaConsumer, Collection<TopicPartition> topicPartitions) {
    final var endOffsets = kafkaConsumer.endOffsets(topicPartitions);

    return kafkaConsumer
        .offsetsForTimes(
            topicPartitions.stream().collect(toMap(Function.identity(), _ignored -> timestampMs)))
        .entrySet()
        .stream()
        .collect(
            toMap(
                Map.Entry::getKey,
                entry -> {
                  if (Objects.nonNull(entry.getValue())) {
                    return entry.getValue().offset();
                  } else {
                    return endOffsets.get(entry.getKey());
                  }
                }));
  }
}
