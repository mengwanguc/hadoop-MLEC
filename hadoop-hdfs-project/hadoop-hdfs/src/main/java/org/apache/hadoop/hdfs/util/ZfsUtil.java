package org.apache.hadoop.hdfs.util;

import org.apache.hadoop.fs.StorageType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ZfsUtil {

    public static boolean onlyStorageType(StorageType[] storageTypes, StorageType type) {
        Set<StorageType> types = Arrays.stream(storageTypes).collect(Collectors.toSet());
        return types.size() == 1 && types.contains(type);
    }

}
