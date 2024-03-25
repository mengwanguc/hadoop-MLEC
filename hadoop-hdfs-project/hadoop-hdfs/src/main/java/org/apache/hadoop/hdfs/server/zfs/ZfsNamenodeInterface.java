package org.apache.hadoop.hdfs.server.zfs;

import org.apache.hadoop.hdfs.protocol.BlockListAsLongs;
import org.apache.hadoop.hdfs.server.blockmanagement.BlockInfo;
import org.apache.hadoop.hdfs.server.blockmanagement.DatanodeDescriptor;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.namenode.Namesystem;
import org.apache.hadoop.hdfs.server.protocol.DatanodeStorage;
import org.apache.hadoop.hdfs.server.zfs.common.ZfsFailureTuple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ZfsNamenodeInterface {

    // This is for R_min
    public List<ZfsFailureTuple> getDataNodeZfsFailureTuples(DatanodeDescriptor datanode) {
        // This needs to talk to ZFS through custom JDK/JNI
        // TODO: Dummy implementation

        return null;
    }

    /**
     * Returns failed stripes on data node. This is for R_fco.
     *
     * @param datanode the data node to look for.
     * @return list of bloc info.
     */
    public List<BlockInfo> getDataNodeZfsFailedStripes(DataNode datanode, final Namesystem ns, String blockPoolId) {

        // TODO: Dummy implementation

        // Randomly select a block sitting on the datanode
        Map<DatanodeStorage, BlockListAsLongs> report = datanode.getFSDataset().getBlockReports(blockPoolId);
        long[] blocks = new ArrayList<>(report.entrySet()).get(0).getValue().getBlockListAsLongs();

        List<BlockInfo> blockInfos = new ArrayList<>();
        for (long blockId : blocks) {
            // Convert block id to BlockInfo (from fsck command)
            blockInfos.addAll(getBlockInfo(ns, blockId));
        }
        return blockInfos;
    }

    private List<BlockInfo> getBlockInfo(final Namesystem ns, final long blockId) {
        // This is not quite what we want
        return Arrays.asList(ns.getBlockCollection(blockId).getBlocks());
    }


}
