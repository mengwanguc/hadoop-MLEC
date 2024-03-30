# Grabs the hadoop-VERSION-SNAPSHOT.tar.gz from hadoop-dist/target folder and construct a a HDFS docker
# Assume that this script is ran from the dockers folder
FROM eclipse-temurin:11-alpine

# Arguments
ARG CURRENT_USER=aaronmao
ARG VERSION=3.5.0
ARG HADOOP_NAME=hadoop-$VERSION-SNAPSHOT

# Copy over the tar.gz
WORKDIR /opt
COPY ./hadoop-dist/target/$HADOOP_NAME.tar.gz /opt

# Unpack the build artifact
RUN tar -xvf $HADOOP_NAME.tar.gz -C /opt
RUN chmod +x /opt/$HADOOP_NAME/bin/hdfs
RUN mkdir /opt/$HADOOP_NAME/logs

# Copy the namenode entry point
COPY ./dockers/namenode-entrypoint.sh /opt/$HADOOP_NAME/sbin/
RUN chmod +x /opt/$HADOOP_NAME/sbin/namenode-entrypoint.sh

# Copy the datanode entry point
COPY ./dockers/datanode-entrypoint.sh /opt/$HADOOP_NAME/sbin/
RUN chmod +x /opt/$HADOOP_NAME/sbin/datanode-entrypoint.sh

# Copy the datanode test init script
COPY ./dockers/seed_test.sh /opt/seed_test.sh
RUN chmod +x /opt/seed_test.sh

# Create a data directory for HDFS, we will point to this directory in config
RUN mkdir -p /data/dataNode1
RUN mkdir -p /data/dataNode2
RUN mkdir -p /data/nameNode

# bash is the preferred shell
RUN apk update
RUN apk add bash openssh sudo openrc util-linux

# Start the ssh service
RUN rc-status \
    # touch softlevel because system was initialized without openrc
    && touch /run/openrc/softlevel

# Configure key based ssh
RUN mkdir ~/.ssh
COPY ./dockers/hadoop-id-rsa /home/${CURRENT_USER}/.ssh/id_rsa
COPY ./dockers/hadoop-id-rsa.pub /home/${CURRENT_USER}/.ssh/id_rsa.pub
COPY ./dockers/hadoop-id-rsa.pub /home/${CURRENT_USER}/.ssh/authorized_keys
COPY ./dockers/hadoop-ssh-config /home/${CURRENT_USER}/.ssh/config

# Configure ENV variables
ENV HADOOP_HOME=/opt/$HADOOP_NAME
ENV PATH="$HADOOP_HOME/sbin:$HADOOP_HOME/bin:$PATH"
ENV HDFS_DATANODE_USER=root
ENV HDFS_NAMENODE_USER=root
ENV HDFS_SECONDARYNAMENODE_USER=root

ENV JAVA_HOME=/opt/java/openjdk