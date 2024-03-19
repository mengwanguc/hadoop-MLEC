# Create 2.5G of a test file
dd if=/dev/urandom of="$1.random" bs=256M count=8
hdfs ec -setPolicy -path /
hdfs dfs -mkdir -p /user/root/test
hdfs dfs -put "$1.random" test