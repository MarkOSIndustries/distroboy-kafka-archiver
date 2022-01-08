package com.markosindustries.distroboy.kafka.archiver;

import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

public class ArchiveRecord {
  public String topic;
  public int partition;
  public long offset;
  public long timestamp;
  public Map<String, byte[]> headers;
  public byte[] key;
  public byte[] value;

  // Needed for deserialisation
  public ArchiveRecord() {}

  public ArchiveRecord(ConsumerRecord<byte[], byte[]> consumerRecord) {
    this.topic = consumerRecord.topic();
    this.partition = consumerRecord.partition();
    this.offset = consumerRecord.offset();
    this.timestamp = consumerRecord.timestamp();
    this.headers =
        Arrays.stream(consumerRecord.headers().toArray())
            .collect(toMap(Header::key, Header::value));
    this.key = consumerRecord.key();
    this.value = consumerRecord.value();
  }
}
