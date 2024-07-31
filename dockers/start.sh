#!/bin/bash

# Check if at least one argument is provided
if [ $# -eq 0 ]; then
  echo "Usage: $0 dn1|dn2|dn3|namenode"
  exit 1
fi

# Function to start a data node
start_datanode() {
  local dn=$1
  local pid_dir="/tmp/hdfs/$dn"
  local conf_dir="/home/aaronmao/hadoop-MLEC/dockers/$dn"

  export HADOOP_PID_DIR=$pid_dir
  export HADOOP_CONF_DIR=$conf_dir

  echo "Starting DataNode $dn with HADOOP_PID_DIR=$HADOOP_PID_DIR and HADOOP_CONF_DIR=$HADOOP_CONF_DIR"
  hdfs datanode | tee "log-$dn.log"
}

# Function to start the name node
start_namenode() {
  local conf_dir="/home/aaronmao/hadoop-MLEC/dockers"

  export HADOOP_CONF_DIR=$conf_dir

  echo "Starting NameNode with HADOOP_CONF_DIR=$HADOOP_CONF_DIR"
  hdfs namenode -format -force
  hdfs namenode
}

# Iterate over all provided arguments and start the respective nodes
for node in "$@"; do
  case $node in
    dn1|dn2|dn3)
      start_datanode $node
      ;;
    namenode)
      start_namenode
      ;;
    *)
      echo "Invalid argument: $node"
      echo "Usage: $0 dn1|dn2|dn3|namenode"
      ;;
  esac
done
