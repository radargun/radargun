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

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;
import org.radargun.utils.Utils;

/**
 * Generates random data to fill the cache. The seed used to instantiate the java.util.Random object
 * can be specified, so that the same data is generated between runs. To specify generating a fixed
 * amount of data in the cache, specify the valueSize and valueCount parameters. The number of
 * values will be divided by the return value of <code>getActiveSlaveCount()</code>. To generate
 * data based on the amount of available RAM, specify the valueSize and ramPercentage parameters.
 * The amount of free memory on each node will be calculated and then used to determine the number
 * of values that are written by the node.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Generates random data to fill the cache.")
public class RandomDataStage extends AbstractDistStage {
   private long nodeCount;

   @Property(doc = "The seed to use for the java.util.Random object. "
         + "The default is the return value of System.nanoTime().")
   private long randomSeed = System.nanoTime();

   @Property(doc = "The size of the values to put into the cache. The default size is 1MB (1024 * 1024).")
   private int valueSize = 1024 * 1024;

   @Property(doc = "The number of values of valueSize to write to the cache. "
         + "Either valueCount or ramPercentageDataSize should be specified, but not both.")
   private long valueCount = -1;

   @Property(doc = "A double that represents the percentage of available RAM "
         + "used to determine the amount of data to put into the cache."
         + "Either valueCount or ramPercentageDataSize should be specified, but not both.")
   private double ramPercentage = -1;

   @Property(doc = "The name of the bucket where keys are written. The default is null")
   private String bucket = null;

   @Property(doc = "If true, then the bytes written to the cache are all printable characters. "
         + "The default is false")
   private boolean stringData = false;

   @Property(doc = "If true, then the time for each put operation is written to the logs. " + "The default is false")
   private boolean printWriteStatistics = false;

   private Random random;

   @Override
   public void initOnSlave(SlaveState slaveState) {
      super.initOnSlave(slaveState);
      random = new Random(randomSeed);
   }

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = newDefaultStageAck();
      CacheWrapper cacheWrapper = slaveState.getCacheWrapper();

      if (cacheWrapper == null) {
         result.setError(true);
         result.setErrorMessage("Not running test on this slave as the wrapper hasn't been configured.");
         return result;
      }

      if (ramPercentage > 0 && valueCount > 0) {
         result.setError(true);
         result.setErrorMessage("Either valueCount or ramPercentageDataSize should be specified, but not both");
         return result;
      }

      if (ramPercentage > 1) {
         result.setError(true);
         result.setErrorMessage("The percentage of RAM can not be greater than one.");
         return result;
      }

      if (ramPercentage > 0) {
         System.gc();
         nodeCount = (long) Math.ceil(Runtime.getRuntime().freeMemory() * this.ramPercentage / this.valueSize);
      } else {
         nodeCount = (long) Math.ceil(valueCount / getActiveSlaveCount());
         /*
          * Add one to the nodeCount on each slave with an index less than the remainder so that the
          * correct number of values are written to the cache
          */
         if ((valueCount % getActiveSlaveCount() != 0) && getSlaveIndex() < (valueCount % getActiveSlaveCount())) {
            nodeCount++;
         }
      }

      long putCount = nodeCount;
      long bytesWritten = 0;
      try {
         byte[] buffer = new byte[valueSize];
         while (putCount > 0) {
            String key = Integer.toString(getSlaveIndex()) + "-" + random.nextLong();

            if (putCount % 5000 == 0) {
               log.info(putCount + ": Writing " + valueSize + " bytes to cache key: " + key);
            }

            buffer = generateRandomData(valueSize, stringData);

            long start = System.nanoTime();
            cacheWrapper.put(bucket, key, buffer);
            if (printWriteStatistics) {
               log.info("Put on slave-" + this.getSlaveIndex() + " took "
                     + Utils.prettyPrintTime(System.nanoTime() - start, TimeUnit.NANOSECONDS));
            }
            putCount--;
            bytesWritten += buffer.length;
         }
         result.setPayload(new long[] { nodeCount, bytesWritten });
      } catch (Exception e) {
         log.fatal("An exception occurred", e);
         result.setError(true);
         result.setErrorMessage("An exception occurred");
      }

      return result;
   }

   private byte[] generateRandomData(int dataSize, boolean useChars) {
      byte[] buffer = null;
      if (useChars) {
         /*
          * Generate a random string of "words" using random single and multi-byte characters that
          * are separated by punctuation marks and whitespace.
          */
         String singleByteChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
         String multiByteChars = "ÅÄÇÉÑÖÕÜàäâáãçëèêéîïìíñôöòóüûùúÿ";
         String punctuationChars = "!,.;?";
         int maxWordLength = 20;

         StringBuffer data = new StringBuffer();
         while (data.toString().getBytes().length < dataSize) {
            int wordLength = random.nextInt(maxWordLength);
            for (int i = 0; i < wordLength; i++) {
               if (random.nextBoolean() || dataSize - data.toString().getBytes().length == 1) {
                  data.append(singleByteChars.charAt(random.nextInt(singleByteChars.length() - 1)));
               } else {
                  data.append(multiByteChars.charAt(random.nextInt(multiByteChars.length() - 1)));
               }
            }

            if (random.nextBoolean()) {
               data.append(punctuationChars.charAt(random.nextInt(punctuationChars.length() - 1)));
            }

            if (random.nextBoolean()) {
               data.append(' ');
            } else {
               data.append('\n');
            }
         }

         //character != byte
         buffer = Arrays.copyOf(data.toString().getBytes(), dataSize);
      } else {
         buffer = new byte[dataSize];
         random.nextBytes(buffer);
      }
      return buffer;
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      super.processAckOnMaster(acks, masterState);
      log.info("--------------------");
      long totalValues = 0;
      long totalBytes = 0;
      for (DistStageAck ack : acks) {
         long[] result = (long[]) ((DefaultDistStageAck) ack).getPayload();
         log.info("Slave " + ((DefaultDistStageAck) ack).getSlaveIndex() + " wrote " + result[0]
               + " values to the cache with a total size of " + result[1] + " bytes");
         totalValues += result[0];
         totalBytes += result[1];
      }
      log.info("The cache contains " + totalValues + " values with a total size of " + totalBytes + " bytes");
      log.info("--------------------");
      return true;
   }
}
