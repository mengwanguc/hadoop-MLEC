package org.apache.hadoop.hdfs.server.datanode.erasurecode;

import io.netty.buffer.ByteBuf;
import org.apache.hadoop.hdfs.server.datanode.DataNodeFaultInjector;
import org.apache.hadoop.hdfs.server.datanode.metrics.DataNodeMetrics;
import org.apache.hadoop.hdfs.util.ZfsUtil;
import org.apache.hadoop.io.erasurecode.rawcoder.InvalidDecodingException;
import org.apache.hadoop.util.Time;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;

public class StripedZfsBlockReconstructor extends StripedBlockReconstructor
        implements Runnable {

  private StripedWriter stripedWriter;

  private List<Integer> zfsRepairIndices;

  private int zfsRepairingIndex;

  // This is a Datanode<ZFSIndices<ByteBuffer>>
  private List<List<ByteBuffer>> zioBuffer;

  StripedZfsBlockReconstructor(ErasureCodingWorker worker, StripedReconstructionInfo stripedReconstructionInfo) {
    super(worker, stripedReconstructionInfo);

    stripedWriter = new StripedWriter(this, getDatanode(), getConf(), stripedReconstructionInfo);

    this.zfsRepairIndices = stripedReconstructionInfo.getZfsFailureIndices();
    LOG.info("StripedZfsBlockReconstructor initialized with zfsRepairIndices {}", this.zfsRepairIndices);

    if (stripedReconstructionInfo.getZfsFailureIndices() == null || this.zfsRepairIndices.isEmpty()) {
      throw new IllegalArgumentException("ZFS repair indices cannot be null or empty");
    }

  }

  @Override
  public void run() {
    try {
      LOG.info("Initializing decoder");
      initDecoderIfNecessary();

      LOG.info("Initializing decoder validator");
      initDecodingValidatorIfNecessary();

      getStripedReader().init();
      LOG.info("Got stripe reader");

      // Note, this step will try to create the block on the target datanode first for later data append
      // This is what is throwing the DiskOutOfSpaceException. For ZFS, we need to tell HDFS to hold the block in memory?
      stripedWriter.init();
      LOG.info("Initialized stripe writer with target status {}", stripedWriter.targetsStatus);
      Thread.sleep(5000);

      // For each of the zfs repair indices, we should do a reconstruction
      for (int zfsIndex : this.zfsRepairIndices) {
        this.zfsRepairingIndex = zfsIndex;
        // Set start and end position of this repair
        updatePositionInBlock(ZfsUtil.ZFS_BLOCK_SIZE_BYTES * zfsIndex);
        setMaxTargetLength(ZfsUtil.ZFS_BLOCK_SIZE_BYTES * (zfsIndex + 1));
        LOG.info("StripedZfsBlockReconstructor - ZFS repair index {}, starting at {}, ending at {}",
                zfsIndex,
                getPositionInBlock(),
                getMaxTargetLength());

        reconstruct();
        LOG.info("Reconstructed {}", zfsIndex);
      }

      stripedWriter.endTargetBlocks();

      LOG.info("Target block ended");

      // Currently we don't check the acks for packets, this is similar as
      // block replication.
    } catch (Throwable e) {
      LOG.warn("Failed to reconstruct striped block: {}", getBlockGroup(), e);
      getDatanode().getMetrics().incrECFailedReconstructionTasks();
    } finally {
      float xmitWeight = getErasureCodingWorker().getXmitWeight();
      // if the xmits is smaller than 1, the xmitsSubmitted should be set to 1
      // because if it set to zero, we cannot to measure the xmits submitted
      int xmitsSubmitted = Math.max((int) (getXmits() * xmitWeight), 1);
      getDatanode().decrementXmitsInProgress(xmitsSubmitted);
      final DataNodeMetrics metrics = getDatanode().getMetrics();
      metrics.incrECReconstructionTasks();
      metrics.incrECReconstructionBytesRead(getBytesRead());
      metrics.incrECReconstructionRemoteBytesRead(getRemoteBytesRead());
      metrics.incrECReconstructionBytesWritten(getBytesWritten());
      getStripedReader().close();
      stripedWriter.close();
      cleanup();

      // MLEC - clear repair bookkeeping maps
      LOG.info("Clearing ongoing repair map entry {}", getBlockGroup().getBlockId());
      try {
        Thread.sleep(5000);
      } catch (Exception e) {
        LOG.error("Error while sleeping", e);
      }
      
      // ErasureCodingWorker.ongoingRepairs.remove(getBlockGroup().getBlockId());
    }
  }

  @Override
  void reconstruct() throws IOException {
    long loopStart = Time.monotonicNow();
    LOG.warn("Reconstruction started for zfs index {} at {} at pos {} and len {}",
            this.zfsRepairingIndex,
            Instant.ofEpochMilli(Time.now()).toString(),
            getPositionInBlock(),
            getMaxTargetLength());
    long bytesRead = 0;
    long bytesTransferred = 0;

    while (getPositionInBlock() < getMaxTargetLength()) {
      long remaining = getMaxTargetLength() - getPositionInBlock();
      final int toReconstructLen =
          (int) Math.min(getStripedReader().getBufferSize(), remaining);

      long start = Time.monotonicNow();
      long bytesToRead = (long) toReconstructLen * getStripedReader().getMinRequiredSources();
      bytesRead += bytesToRead;
      if (getDatanode().getEcReconstuctReadThrottler() != null) {
        getDatanode().getEcReconstuctReadThrottler().throttle(bytesToRead);
      }

      // step1: read from minimum source DNs required for reconstruction.
      // The returned success list is the source DNs we do real read from
      LOG.info("Read total of {} bytes as sources", toReconstructLen);
      getStripedReader().readMinimumSources(toReconstructLen);
      LOG.info("Reading complete");
      long readEnd = Time.monotonicNow();

      // step2: decode to reconstruct targets
      LOG.info("Reconstructing {} targets", toReconstructLen);
      this.reconstructTargets(toReconstructLen);
      long decodeEnd = Time.monotonicNow();

      // step3: transfer data
      long bytesToWrite = (long) toReconstructLen * stripedWriter.getTargets();
      bytesTransferred += bytesToWrite;
      if (getDatanode().getEcReconstuctWriteThrottler() != null) {
        getDatanode().getEcReconstuctWriteThrottler().throttle(bytesToWrite);
      }


      LOG.info("Starting transfer of {} bytes to target datanode", bytesToWrite);
      if (stripedWriter.transferData2Targets() == 0) {
        String error = "Transfer failed for all targets.";
        throw new IOException(error);
      }
      long writeEnd = Time.monotonicNow();

      // Only the succeed reconstructions are recorded.
      final DataNodeMetrics metrics = getDatanode().getMetrics();
      metrics.incrECReconstructionReadTime(readEnd - start);
      metrics.incrECReconstructionDecodingTime(decodeEnd - readEnd);
      metrics.incrECReconstructionWriteTime(writeEnd - decodeEnd);

      updatePositionInBlock(toReconstructLen);

      clearBuffers();
    }

    LOG.warn("Reconstruction ended at {}, total {} seconds", Instant.ofEpochMilli(Time.now()).toString(),
            (Time.monotonicNow() - loopStart));
    LOG.warn("Total read {} bytes, wrote {} bytes", bytesRead, bytesTransferred);
  }

  protected void reconstructTargets(int toReconstructLen) throws IOException {
    ByteBuffer[] inputs = getStripedReader().getInputBuffers(toReconstructLen);

    int[] erasedIndices = stripedWriter.getRealTargetIndices();
    ByteBuffer[] outputs = stripedWriter.getRealTargetBuffers(toReconstructLen);

    if (isValidationEnabled()) {
      markBuffers(inputs);
      decode(inputs, erasedIndices, outputs);
      resetBuffers(inputs);

      DataNodeFaultInjector.get().badDecoding(outputs);
      long start = Time.monotonicNow();
      try {
        getValidator().validate(inputs, erasedIndices, outputs);
        long validateEnd = Time.monotonicNow();
        getDatanode().getMetrics().incrECReconstructionValidateTime(
                validateEnd - start);
      } catch (InvalidDecodingException e) {
        long validateFailedEnd = Time.monotonicNow();
        getDatanode().getMetrics().incrECReconstructionValidateTime(
                validateFailedEnd - start);
        getDatanode().getMetrics().incrECInvalidReconstructionTasks();
        throw e;
      }
    } else {
      decode(inputs, erasedIndices, outputs);
    }

    stripedWriter.updateRealTargetBuffers(toReconstructLen);
  }

  private void allocateBuffer() {
    // How big the buffer needs to be? Each should have the entire block size
  }
}
