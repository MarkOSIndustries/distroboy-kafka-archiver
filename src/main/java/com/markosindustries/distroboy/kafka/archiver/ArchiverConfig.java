package com.markosindustries.distroboy.kafka.archiver;

import java.util.List;
import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({"system:env", "classpath:config.properties"})
public interface ArchiverConfig extends Config {
  @Config.Key("ARCHIVER_START_TIMESTAMP_MS")
  long startTimestampMs();

  @Config.Key("ARCHIVER_END_TIMESTAMP_MS")
  long endTimestampMs();

  @Config.Key("ARCHIVER_TOPICS")
  List<String> topics();
}
