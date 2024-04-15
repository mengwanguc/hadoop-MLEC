package org.apache.hadoop.hdfs.util;

import org.apache.hadoop.fs.StorageType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ZfsUtil {

    // Move this configuration into config.xml in the final version
    // This is 256KB, 1/4 of the HDFS block size, which is 1MB
    public static int ZFS_BLOCK_SIZE_BYTES = 262144;

    public static boolean onlyStorageType(StorageType[] storageTypes, StorageType type) {
        Set<StorageType> types = Arrays.stream(storageTypes).collect(Collectors.toSet());
        return types.size() == 1 && types.contains(type);
    }

}
