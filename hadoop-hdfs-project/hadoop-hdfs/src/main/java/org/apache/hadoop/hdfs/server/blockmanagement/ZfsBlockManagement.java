package org.apache.hadoop.hdfs.server.blockmanagement;

import org.apache.hadoop.hdfs.protocol.BlockListAsLongs;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.namenode.Namesystem;
import org.apache.hadoop.hdfs.server.protocol.DatanodeStorage;
import org.apache.hadoop.hdfs.server.zfs.common.ZfsFailureTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ZfsBlockManagement {

    public static final Logger LOG = LoggerFactory.getLogger(ZfsBlockManagement.class.getName());

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
    public static List<Long> getDataNodeZfsFailedStripes(DatanodeDescriptor datanode) {

        // Randomly select a block sitting on the datanode
        // Map<DatanodeStorage, BlockListAsLongs> report = datanode.getFSDataset().getBlockReports(blockPoolId);
        // long[] blocks = new ArrayList<>(report.entrySet()).get(0).getValue().getBlockListAsLongs();
        List<Long> failedBlocks = new ArrayList<>();

        datanode.getBlockIterator().forEachRemaining(blockInfo -> {
            failedBlocks.add(blockInfo.getBlockId());
            LOG.info("BlockInfo {} on datanode {}", blockInfo, datanode.getHostName());
        });
        
        // Always mark the first one as failed
        // TODO: return the first block on the datanode as failed
        return Collections.singletonList(failedBlocks.get(0));
        // return failedBlocks;
    }

}
