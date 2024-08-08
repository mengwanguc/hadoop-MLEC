package org.apache.hadoop.hdfs.server.datanode;

import org.apache.hadoop.hdfs.server.blockmanagement.ZfsFailureTuple;

import java.util.HashMap;
import java.util.Map;

public class MlecDatanodeManagement {

    public Map<Long, ZfsFailureTuple> knownFailures = new HashMap<>();

    public MlecDatanodeManagement() {
        this.knownFailures = new HashMap<>();
    }
}
