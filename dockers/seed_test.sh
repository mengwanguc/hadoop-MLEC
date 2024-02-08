dd if=/dev/urandom of=test.random bs=256M count=20
hdfs ec -setPolicy -path /
hdfs dfs -mkdir -p /user/root/test
hdfs dfs -put test.random test