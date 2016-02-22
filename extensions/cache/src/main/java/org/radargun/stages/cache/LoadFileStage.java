package org.radargun.stages.cache;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * Loads the contents of the specified file into the cache using the specified sized values. All
 * slaves are used to read from the file and write keys to the cache.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Loads the contents of a file into the cache.")
public class LoadFileStage extends AbstractDistStage {

   @Property(optional = false, doc = "Full pathname to the file.")
   private String filePath;

   @Property(doc = "The size of the values to put into the cache from the contents"
      + " of the file. The default size is 1MB (1024 * 1024).")
   private int valueSize = 1024 * 1024;

   @Property(doc = "The name of the bucket where keys are written. The default is null.")
   private String bucket = null;

   @Property(doc = "If true, then the time for each put operation is written to the logs. The default is false.")
   private boolean printWriteStatistics = false;

   @Property(doc = "If true, then String objects are written to the cache. The default is false.")
   private boolean stringData = false;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private BasicOperations basicOperations;

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      long fileSize = new File(filePath).length();
      log.info("--------------------");
      log.info("Size of file '" + filePath + "' is " + fileSize + " bytes");
      log.info("Value size is '" + valueSize + "' which will produce " + (int) Math.ceil((double) fileSize / valueSize)
         + " keys");
      for (ResultAck ack : instancesOf(acks, ResultAck.class)) {
         log.info("Slave " + ack.getSlaveIndex() + " wrote " + ack.putCount
            + " values to the cache with a total size of " + ack.totalBytesRead + " bytes");
      }
      log.info("--------------------");
      return StageResult.SUCCESS;
   }

   @Override
   public DistStageAck executeOnSlave() {
      int totalWriters = slaveState.getClusterSize();
      long fileOffset = valueSize * slaveState.getSlaveIndex(); // index starts at 0

      RandomAccessFile file = null;
      FileChannel fileChannel = null;
      long totalBytesRead = 0;
      long putCount = 0;

      try {
         file = new RandomAccessFile(filePath, "r");
         fileChannel = file.getChannel();
         fileChannel.position(fileOffset);

         ByteBuffer buffer = ByteBuffer.allocate(valueSize);
         BasicOperations.Cache cache = basicOperations.getCache(bucket);
         while (true) {
            long initPos = fileChannel.position();
            String key = Integer.toString(slaveState.getSlaveIndex()) + "-" + Long.toString(initPos);
            int bytesRead = fileChannel.read(buffer);

            if (bytesRead != -1) {
               while (bytesRead != valueSize) {
                  int readBytes = fileChannel.read(buffer);
                  if (readBytes == -1) {
                     break;
                  } else {
                     bytesRead += readBytes;
                  }
               }
               totalBytesRead += bytesRead;
               if (putCount % 5000 == 0) {
                  log.info("Writing " + bytesRead + " bytes to cache key: " + key + " at position "
                     + fileChannel.position());
               }
               buffer.rewind();
               long start = TimeService.nanoTime();
               if (stringData) {
                  String cacheData = buffer.asCharBuffer().toString();
                  start = TimeService.nanoTime();
                  cache.put(key, cacheData);
               } else {
                  cache.put(key, buffer.array());
               }
               if (printWriteStatistics) {
                  log.info("Put on slave-" + slaveState.getSlaveIndex() + " took "
                     + Utils.prettyPrintTime(TimeService.nanoTime() - start, TimeUnit.NANOSECONDS));
               }
               putCount++;
               fileChannel.position(initPos + (valueSize * totalWriters));
               buffer.clear();
            } else {
               file.close();
               file = null;
               break;
            }
         }
         return new ResultAck(slaveState, putCount, totalBytesRead);
      } catch (FileNotFoundException e) {
         return errorResponse("File not find at path: " + filePath, e);
      } catch (Exception e) {
         return errorResponse("An exception occurred", e);
      } finally {
         if (file != null) {
            try {
               file.close();
            } catch (IOException e) {
               log.fatal("An exception occurred closing the file", e);
            }
         }
      }
   }

   private static class ResultAck extends DistStageAck {
      final long putCount;
      final long totalBytesRead;

      private ResultAck(SlaveState slaveState, long putCount, long totalBytesRead) {
         super(slaveState);
         this.putCount = putCount;
         this.totalBytesRead = totalBytesRead;
      }

      @Override
      public String toString() {
         return "ResultAck{" +
            "putCount=" + putCount +
            ", totalBytesRead=" + totalBytesRead +
            "} " + super.toString();
      }
   }
}
