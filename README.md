## Introduction
HDFS component for MLEC implementation

## How to build
All the build scripts are located in the `dockers` folder. **Note**: we are currently **NOT** using docker to orchestrate datanodes, because docker cannot communicate with underlying kernels. We are setting up multiple HDFS datanodes as separate Java processes on the host machine directly.

### Setting up dockers (DEPRECATED)
We can setup a real("sort of") HDFS cluster by spinning up a single HDFS DN instance as one docker container. This is useful when you don't need any interaction from ZFS.

Follow the below to get started
1. Modify `docker/docker-compose.yml`'s `replica` setting to determine how many DN you want to have
2. Run `./build-restart.sh`
   1. The script will prompt whether you want to use build the HDFS source code, if you have made changes, you should type `Y`. Otherwise, type `N` to avoid wasting time building new artifacts
      1. **Note**: currently the script only build `hadoop-common-project` and `hadoop-hdfs-project` (building `hdfs-dist` is not related to functionality, just to generate the JAR file). If you want to build other sub Maven projects, you should modify the script
   2. The script will then stop all the running docker containers, create new docker images, and then restart with the newly built changes

### Setting up HDFS as local processors (MLEC)
We can also setup HDFS instances as local Java processes using different port numbers. This will allow HDFS DN to communicate with ZFS via `mlec-java-binding` through Linux `ioctl` system call.

Before building, you would need to follow the `BUILDING.txt` to install required toolchain on your local computer.

Follow the below to get started
1. Have python installed
2. Run `python docker/start_all.py`
   1. This will spawn 3 datanodes and 1 namenode in separate processes. It will also pipe the STDOUT and STDERR from those processes into a single window with colored prefix.
   2. To adjust the level of logging, change the `ENV_VAR` dictionary in the python script
   3. This script will also prompt you for building the source code or not. Same as the docker method, it will also only build `hadoop-common-project` and `hadoop-hdfs-project`

## Project Structure
Unlike ZFS, HDFS logics are really scattered in a lot of files.

In general, the important code segments have been commented with `MLEC stuff`. Doing a global search should reveal their locations.

### `hadoop-common-project` 
This directory contains a few common attributes used by HDFS. It also contains some `protobuf` definitions that are used to pass message between datanodes and namenodes.

Most of the changes in this directory are logging and protobuf definitions. There are minimal logic changes. I will list the changes in this directory, omitting the base path `hadoop-common-project/hadoop-common/src/main`

- `/java/org/apache/hadoop/fs/StorageType.java` - Added ZFS storage type, so that we can enable special logics for ZFS pool based HDFS volumes (otherwise, ZFS pool will be registered as normal DISK, making logic modification difficult)
- `/proto/datatransfer.proto` - Added `ZFS_RECONSTRUCTION` type to `OpWriteBlockProto` so that we can know a block write is a ZFS reconstruction rather than normal write

### `hadoop-hdfs-project/hadoop-hdfs-client`
This contains a few code changes related to HDFS network packets and IO definitions used for communication from DN to NN, or between DN.

```
.
├── proto/
│   ├── hdfs.proto: ZFS storage type in protobuf enum
│   ├── erasurecoding.proto: contains protobuf information needed for EC repair (failure indices and local block id)
│   └── datatransfer.proto: contains the column index(same as the failure indices above, also used for repair)
└── java/
    └── .../hdfs/
        ├── protocol/
        │   └── datatransfer/
        │       └── PacketHeader.java: adapt java class for the new protobuf definition
        ├── server/
        │   └── protocol/
        │       └── ZfsFailureReport.java: ZFS failure report that is attached to every heartbeat from DN to NN
        ├── protocolPB/
        │   └── PBHelperClient.java: a bunch of translation between protobuf and java class for sending and receiving
        └── DFSPacket.java: the "packet" that HDFS uses to send commands between DN and NN. Include special headers for MLEC repair commands corresponding to changes in protobuf definition.
```

### `hadoop-hdfs-project/hadoop-hdfs`
Some helper stuff under this directory

```
.
└── hdfs/
    ├── util/
    │   └── ZfsUtil.java: util classes for calling mlec-java-binding which in turn calls ZFS
    ├── protocolPB/
    │   └── A bunch of protobuf and tranlsation related stuff
    └── server/
        ├── zfs/
        │   └── contains a few helper classes
        ├── protocol/
        │   └── again, protobuf translation stuff
```

**Important**, all the namenode logics are here
- Handle heartbeat from DN
- Get ZFS failure information from the DN's heartbeat report
- Calculate the necessary repair
- Schedule the repair work to the repair destination (failure source)

```
.
└── hdfs/server/blockmanagement/
    ├── BlockManager.java/
    │   ├── VERY IMPORTANT
    │   ├── RedundancyMonitor Runnable is responsible for computing repair information, and scheduling repair jobs starting from line 5522. 
    │   └── Also responsible for tracking what blocks have failed on which DN for customized MLEC repair logic/
    │       └── We choose the failure source as the repair destination, rather than other DN in the replica set
    ├── BlockStoragePolicySuite.java: allow blocks to sit on ZFS storage type, rather than just DISK by default
    ├── DatanodeDescriptor.java: add customized BlockReconstructionInfo builder logic to attach MLEC repair info
    ├── DatanodeManager.java: handle heartbeat coming from DN that contains ZFS failure report. Then queue the repair into BlockManager so that RedundancyMonitor can do its job
    ├── HeartbeatManager.java: manages heartbeat interval. Will mark DN as dead if no ping is received for a period of time.
    └── ZfsBlockManagement.java: just a wrapper class for a map to record failure info
```

**Important**, all the datanode logics are here
- Report ZFS failure to namenode
- Receive reconstruction command from namenode, execute the repair

```
.
└── hdfs/server/datanode/
    ├── erasurecode/
    │   ├── ErasureCodingWorker.java: schedule reconstruction task with MLEC information
    │   ├── StripedBlockReconstructor.java: do some book-keeping while doing reconstruction
    │   ├── StripedBlockWriter.java: custom MLEC data write logic
    │   └── StripedZfsBlockReconstructor.java: extends the previous `SripedBlockReconstructor` class, provide local chunk sized reconstruction logic
    ├── fsdataset/
    │   ├── FsDatasetImpl.java
    │   └── FsVolumeImpl.java: small logic for MLEC writes, setting up the output stream
    ├── BPServiceActor.java: sends heartbeat including ZFS failure information from DN to NN
    ├── MLECRepairReplicaInPipeline.java: repair write pipeline
    ├── MlecDatanodeManagement.java: book keeping for on-going repairs at datanode level (prevent calling easy-scrub when there is an ongoing repair)
    └── MlecLocalReplicaInPipeline.java: repair write pipeline
    ```