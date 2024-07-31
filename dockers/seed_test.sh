# Create 2.5G of a test file

dd if=/dev/urandom of="$1.random" bs=1M count=2
hdfs ec -setPolicy -path hdfs://localhost:9000/
hdfs dfs -mkdir -p hdfs://localhost:9000/test
hdfs dfs -put "$1.random" hdfs://localhost:9000/$1.random

# hdfs dfs -touch test.txt