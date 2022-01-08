package com.markosindustries.distroboy.kafka.archiver;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({"system:env", "classpath:config.properties"})
public interface KafkaConfig extends Config {

  @Config.Key("KAFKA_BROKERS")
  @Config.DefaultValue("kafka:9092")
  String kafkaBrokers();
}
