package org.apache.hadoop.hdfs.server.blockmanagement;

import org.apache.hadoop.hdfs.server.blockmanagement.BlockInfo;

import java.util.List;

public class ZfsFailureTuple {

    // ----------------------------------------------
    // The next two fields are from ZFS API
    // Which HDFS block failed
    private Long failedBlock;

    // Which chunks in the HDFS block has failed
    private List<Integer> ecIndex;
    // ----------------------------------------------


    // After grabbing from ZFS API, we will find the DSI from namenode and populate this field
    // This is for convenience
    private DatanodeStorageInfo datanodeStorageInfo;

    public ZfsFailureTuple(final Long failedBlock, List<Integer> ecIndex) {
        this.failedBlock = failedBlock;
        this.ecIndex = ecIndex;
    }

    public Long getFailedBlock() {
        return failedBlock;
    }

    public void setFailedBlock(Long failedBlock) {
        this.failedBlock = failedBlock;
    }

    public List<Integer> getEcIndex() {
        return ecIndex;
    }

    public void setEcIndex(List<Integer> ecIndex) {
        this.ecIndex = ecIndex;
    }

    public DatanodeStorageInfo getDatanodeStorageInfo() {
        return datanodeStorageInfo;
    }

    public void setDatanodeStorageInfo(DatanodeStorageInfo datanodeStorageInfo) {
        this.datanodeStorageInfo = datanodeStorageInfo;
    }

    @Override
    public String toString() {
        return "ZfsFailureTuple{" +
                "failedBlock=" + failedBlock +
                ", ecIndex=" + ecIndex +
                ", datanodeStorageInfo=" + datanodeStorageInfo +
                '}';
    }
}
