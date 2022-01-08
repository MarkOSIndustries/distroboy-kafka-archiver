package com.markosindustries.distroboy.kafka.archiver;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({"system:env", "classpath:config.properties"})
public interface S3Config extends Config {
  @Config.Key("USE_LOCALSTACK_FOR_S3")
  @Config.DefaultValue("false")
  boolean useLocalStackForS3();

  @Config.Key("S3_ENDPOINT_OVERRIDE")
  @Config.DefaultValue("")
  String s3EndpointOverride();

  @Config.Key("AWS_REGION")
  @Config.DefaultValue("ap-southeast-2")
  String awsRegion();

  @Config.Key("S3_BUCKET_NAME")
  @Config.DefaultValue("kafka-archive")
  String bucketName();
}
