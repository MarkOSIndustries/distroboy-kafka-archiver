# DistroBoy Kafka Archiver
[![][license-badge]][license]

An example usage of DistroBoy where Kafka records are archived to S3 in Parquet format

## Usage

The idea here is to run (along with a DistroBoy coordinator) some number of these, and once done, all the records from the requested time range will have been archived.
Currently, the time range must end before the topic does, otherwise we won't find offsets to finish at.

## Things which could be improved
- Keep track of which offset/timestamp we last finished at and pick up from there each time
- Handle timestamps in the future by falling back to latest/earliest
- Add some sort of deployment (terraform/cloud formation/etc) automation for running in AWS

[license-badge]:https://img.shields.io/github/license/MarkOSIndustries/distroboy
[license]:LICENSE
