package org.apache.hadoop.hdfs.server.protocol;

import org.apache.hadoop.hdfs.server.blockmanagement.ZfsFailureTuple;

import java.util.ArrayList;
import java.util.List;

public class ZfsFailureReport {

    private List<ZfsFailureTuple> failedHdfsBlocks;

    public ZfsFailureReport() {
        this.failedHdfsBlocks = new ArrayList<>();
    }

    public ZfsFailureReport(List<ZfsFailureTuple> failedHdfsBlocks) {
        this.failedHdfsBlocks = failedHdfsBlocks;
    }

    public List<ZfsFailureTuple> getFailedHdfsBlocks() {
        return failedHdfsBlocks;
    }

    public void setFailedHdfsBlocks(List<ZfsFailureTuple> failedHdfsBlocks) {
        this.failedHdfsBlocks = failedHdfsBlocks;
    }
}