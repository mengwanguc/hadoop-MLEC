package org.apache.hadoop.hdfs.server.blockmanagement;

import jni.DnodeAttributes;
import org.apache.hadoop.hdfs.server.blockmanagement.BlockInfo;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZfsFailureTuple {

    private final static String regex = "^blk_(-?\\d+)$";
    private final static Pattern pattern = Pattern.compile(regex);

    // ----------------------------------------------
    // The next two fields are from ZFS API
    // Which HDFS block failed
    private Long failedBlock;

    // Which chunks in the HDFS block has failed, if non-zero it means failed
    private List<Integer> ecIndex;
    // ----------------------------------------------


    // After grabbing from ZFS API, we will find the DSI from name node and populate this field
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

    public static Optional<Matcher> isHdfsBlock(DnodeAttributes dnode) {
        String[] blockFilePath = dnode.path.split("/");
        Matcher matcher = pattern.matcher(blockFilePath[blockFilePath.length - 1]);

        if (matcher.matches()) {
            return Optional.of(matcher);
        } else {
            return Optional.empty();
        }
    }
}
