package org.apache.hadoop.hdfs.util;

import org.apache.hadoop.fs.StorageType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ZfsUtil {

    // Move this configuration into config.xml in the final version
    // This is 32KB
    public static int ZFS_BLOCK_SIZE_BYTES = 32768;

    public static boolean onlyStorageType(StorageType[] storageTypes, StorageType type) {
        Set<StorageType> types = Arrays.stream(storageTypes).collect(Collectors.toSet());
        return types.size() == 1 && types.contains(type);
    }

}
