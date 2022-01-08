#!/bin/bash

# Get the reusable infrastructure we need going
docker-compose -p distroboy-kafka-archiver -f localdev/docker-compose.yml up -d

# Run the archiver job
exec docker-compose -p distroboy-kafka-archiver -f docker-compose.yml up --build
