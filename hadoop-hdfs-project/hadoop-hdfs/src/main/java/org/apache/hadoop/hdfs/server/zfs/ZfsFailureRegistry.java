package org.apache.hadoop.hdfs.server.zfs;

import org.apache.hadoop.hdfs.server.blockmanagement.DatanodeStorageInfo;

import java.util.Set;

public class ZfsFailureRegistry {

    public static Set<DatanodeStorageInfo> failedZfsPool;

}
