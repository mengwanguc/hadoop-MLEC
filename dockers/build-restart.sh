#!/bin/bash

read -p "Do you want to build HDFS source code? (y/n) " yn

case $yn in y )
  # Build HDFS
  docker exec hdfs bash -c "cd hadoop-hdfs-project && mvn -T 24 clean package -Pdist -DskipTests -Dtar -Dmaven.javadoc.skip=true"
  docker exec hdfs bash -c "cd hadoop-dist && mvn -T 24 clean package -Pdist -DskipTests -Dtar -Dmaven.javadoc.skip=true"
esac

# Build docker
docker build --no-cache -t hdfs-runtime:latest -f Dockerfile ..

# Restart docker-compose
docker-compose stop && docker-compose rm -f

# Start the docker-compose
docker-compose up -d