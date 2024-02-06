# Build docker
docker build --no-cache -t hdfs-runtime:latest -f Dockerfile ..

# Restart docker-compose
docker-compose stop && docker-compose rm -f

# Start the docker-compose
docker-compose up -d