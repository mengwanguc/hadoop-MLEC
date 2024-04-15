/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.datanode.erasurecode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.hdfs.server.datanode.DataNodeFaultInjector;
import org.apache.hadoop.hdfs.server.datanode.metrics.DataNodeMetrics;
import org.apache.hadoop.io.erasurecode.rawcoder.InvalidDecodingException;
import org.apache.hadoop.util.Time;

/**
 * StripedBlockReconstructor reconstruct one or more missed striped block in
 * the striped block group, the minimum number of live striped blocks should
 * be no less than data block number.
 */
@InterfaceAudience.Private
class StripedBlockReconstructor extends StripedReconstructor
    implements Runnable {

  private StripedWriter stripedWriter;

  private Integer zfsRepairIndex;

  StripedBlockReconstructor(ErasureCodingWorker worker,
      StripedReconstructionInfo stripedReconInfo) {
    this(worker, stripedReconInfo, null);
  }

  StripedBlockReconstructor(ErasureCodingWorker worker,
      StripedReconstructionInfo stripedReconInfo, Integer zfsRepairIndex) {
    super(worker, stripedReconInfo);

    stripedWriter = new StripedWriter(this, getDatanode(),
        getConf(), stripedReconInfo);

    this.zfsRepairIndex = zfsRepairIndex;
    if (this.zfsRepairIndex != null) {
      LOG.info("StripedBlockReconstructor - ZFS repair index {}", this.zfsRepairIndex);
      // Set start and end position of this repair
      stripedWriter.setZfsRepairIndex(this.zfsRepairIndex);
    }
  }

  boolean hasValidTargets() {
    return stripedWriter.hasValidTargets();
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
      LOG.info("Initialized stripe writer");
      Thread.sleep(15000);

      reconstruct();
      LOG.info("Reconstructed");

      stripedWriter.endTargetBlocks();

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
    }
  }

  @Override
  void reconstruct() throws IOException {
    long loopStart = Time.monotonicNow();
    LOG.warn("Reconstruction started at {} at pos {} and len {}",
            Instant.ofEpochMilli(Time.now()).toString(),
            getPositionInBlock(),
            getMaxTargetLength());
    long bytesRead = 0;
    long bytesTransferred = 0;

    if (this.zfsFailedIndices != null) {
      LOG.info("Reconstruction failure indices {}", this.zfsFailedIndices);
    }

    while (getPositionInBlock() < getMaxTargetLength()) {
      DataNodeFaultInjector.get().stripedBlockReconstruction();
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
      reconstructTargets(toReconstructLen);
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

  private void reconstructTargets(int toReconstructLen) throws IOException {
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

  private void decode(ByteBuffer[] inputs, int[] erasedIndices,
      ByteBuffer[] outputs) throws IOException {
    long start = System.nanoTime();
    getDecoder().decode(inputs, erasedIndices, outputs);
    long end = System.nanoTime();
    this.getDatanode().getMetrics().incrECDecodingTime(end - start);
  }

  /**
   * Clear all associated buffers.
   */
  private void clearBuffers() {
    getStripedReader().clearBuffers();

    stripedWriter.clearBuffers();
  }
}
