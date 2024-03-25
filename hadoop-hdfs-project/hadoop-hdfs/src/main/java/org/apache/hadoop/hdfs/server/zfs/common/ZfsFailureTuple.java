package org.apache.hadoop.hdfs.server.zfs.common;

import org.apache.hadoop.hdfs.server.blockmanagement.BlockInfo;

public class ZfsFailureTuple {

    public BlockInfo failedBlock;

    public int ecIndex;

    public ZfsFailureTuple(final BlockInfo failedBlock, int ecIndex) {
        this.failedBlock = failedBlock;
        this.ecIndex = ecIndex;
    }

}
