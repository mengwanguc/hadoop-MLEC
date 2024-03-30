package org.apache.hadoop.hdfs.server.blockmanagement;

import org.apache.hadoop.hdfs.server.blockmanagement.BlockInfo;

public class ZfsFailureTuple {

    private Long failedBlock;

    private int ecIndex;

    public ZfsFailureTuple(final Long failedBlock, int ecIndex) {
        this.failedBlock = failedBlock;
        this.ecIndex = ecIndex;
    }

    public Long getFailedBlock() {
        return failedBlock;
    }

    public void setFailedBlock(Long failedBlock) {
        this.failedBlock = failedBlock;
    }

    public int getEcIndex() {
        return ecIndex;
    }

    public void setEcIndex(int ecIndex) {
        this.ecIndex = ecIndex;
    }
}
