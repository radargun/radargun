/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stages;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
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
         + " of the file. The default size is 1MB (1024 * 1024)")
   private int valueSize = 1024 * 1024;

   @Property(doc = "The name of the bucket where keys are written. The default is null")
   private String bucket = null;

   @Property(doc = "If true, then the time for each put operation is written to the logs. The default is false")
   private boolean printWriteStatistics = false;

   @Property(doc = "If true, then String objects are written to the cache. The default is false")
   private boolean stringData = false;

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      super.processAckOnMaster(acks);
      long fileSize = new File(filePath).length();
      log.info("--------------------");
      log.info("Size of file '" + filePath + "' is " + fileSize + " bytes");
      log.info("Value size is '" + valueSize + "' which will produce " + (int) Math.ceil((double) fileSize / valueSize)
            + " keys");
      for (DistStageAck ack : acks) {
         long[] result = (long[]) ((DefaultDistStageAck) ack).getPayload();
         log.info("Slave " + ((DefaultDistStageAck) ack).getSlaveIndex() + " wrote " + result[0]
               + " values to the cache with a total size of " + result[1] + " bytes");
      }
      log.info("--------------------");
      return true;
   }

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = newDefaultStageAck();
      int totalWriters = slaveState.getClusterSize();
      long fileOffset = valueSize * slaveState.getSlaveIndex();// index starts at 0
      CacheWrapper cacheWrapper = slaveState.getCacheWrapper();

      if (cacheWrapper == null) {
         result.setError(true);
         result.setErrorMessage("Not running test on this slave as the wrapper hasn't been configured.");
         return result;
      }

      RandomAccessFile file = null;
      FileChannel fileChannel = null;
      long totalBytesRead = 0;
      long putCount = 0;

      try {
         file = new RandomAccessFile(filePath, "r");
         fileChannel = file.getChannel();
         fileChannel.position(fileOffset);

         ByteBuffer buffer = ByteBuffer.allocate(valueSize);
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
               long start = System.nanoTime();
               if (stringData) {
                  String cacheData = buffer.asCharBuffer().toString();
                  start = System.nanoTime();
                  cacheWrapper.put(bucket, key, cacheData);
               } else {
                  cacheWrapper.put(bucket, key, buffer.array());
               }
               if (printWriteStatistics) {
                  log.info("Put on slave-" + slaveState.getSlaveIndex() + " took "
                        + Utils.prettyPrintTime(System.nanoTime() - start, TimeUnit.NANOSECONDS));
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
         result.setPayload(new long[] { putCount, totalBytesRead });
      } catch (FileNotFoundException e) {
         log.fatal("File not find at path: " + filePath, e);
         result.setError(true);
         result.setErrorMessage("File not find at path: " + filePath);
      } catch (Exception e) {
         log.fatal("An exception occurred", e);
         result.setError(true);
         result.setErrorMessage("An exception occurred");
      } finally {
         if (file != null) {
            try {
               file.close();
            } catch (IOException e) {
               log.fatal("An exception occurred closing the file", e);
            }
         }
      }

      return result;
   }
}
