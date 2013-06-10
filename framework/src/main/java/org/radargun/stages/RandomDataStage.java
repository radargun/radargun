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

import java.nio.ByteBuffer;
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

   @Property(doc = "If true, then String objects with printable characters are written to the cache."
         + "The default is false")
   private boolean stringData = false;

   @Property(doc = "If true, then the time for each put operation is written to the logs. The default is false")
   private boolean printWriteStatistics = false;

   @Property(doc = "If true, then the random word generator selects a word from a pre-defined list. "
         + "The default is false")
   private boolean limitWordCount = false;

   @Property(doc = "The maximum number of words to generate in the pre-defined list of words used with limitWordCount."
         + "The default is 100.")
   private int maxWordCount = 100;

   @Property(doc = "The maximum number of bytes allowed in a word. The default is 20.")
   private int maxWordByteLength = 20;

   @Property(doc = "If false, then each node in the cluster generates a list of maxWordCount words. "
         + "If true, then each node in the cluster shares the same list of words. The default is false.")
   private boolean shareWords = false;

   private Random random;

   private String[][] words = null;

   private static String SHARED_WORDS_KEY = "random_words";

   @Override
   public void initOnMaster(MasterState masterState, int slaveIndex) {
      super.initOnMaster(masterState, slaveIndex);
      /*
       * Add the slaveIndex to the seed to guarantee that each node generates a different word list
       */
      random = new Random(randomSeed + slaveIndex);

      if (shareWords && limitWordCount) {
         /*
          * Generate the word list and put it in the masterState. Then wait for the other nodes to
          * retrieve the word list.
          */
         if (slaveIndex == 0) {
            fillWordArray(maxWordCount, maxWordByteLength);
            masterState.put(SHARED_WORDS_KEY, words);
         } else {
            int sleepCount = getActiveSlaveCount();
            while (masterState.get(SHARED_WORDS_KEY) == null && sleepCount > 0) {
               try {
                  Thread.sleep(5000);
               } catch (InterruptedException e) {
                  //Eat this
               }
               sleepCount--;
            }
            words = (String[][]) masterState.get(SHARED_WORDS_KEY);
         }
      }
   }

   /**
    * 
    * Fills a multi-dimensional array with randomly generated words. The first dimension of the
    * array is based on the length of the word in bytes, and runs from 1 to maxWordLength. Dividing
    * the wordCount by maxWordLength determines how many words of each length are generated.
    * 
    * @param wordCount
    *           the total number of words to generate
    * @param maxWordLength
    *           the maximum size in bytes for a word
    */
   private void fillWordArray(int wordCount, int maxWordLength) {
      int wordsPerLength = wordCount / maxWordLength;
      words = new String[maxWordLength][wordsPerLength];
      for (int i = 1; i <= maxWordLength; i++) {
         for (int j = 0; j < wordsPerLength; j++) {
            words[i - 1][j] = new String(generateRandomUniqueWord(i, false));
         }
      }
   }

   @Override
   public void initOnSlave(SlaveState slaveState) {
      super.initOnSlave(slaveState);
      if (!shareWords && limitWordCount) {
         fillWordArray(maxWordCount, maxWordByteLength);
      }
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

      if (shareWords && !limitWordCount) {
         result.setError(true);
         result.setErrorMessage("The shareWords property can only be true when limitWordCount is also true.");
         return result;
      }

      if (limitWordCount && !stringData) {
         result.setError(true);
         result.setErrorMessage("The limitWordCount property can only be true when stringData is also true.");
         return result;
      }

      if (ramPercentage > 0) {
         System.gc();
         nodeCount = (long) Math.ceil(Runtime.getRuntime().freeMemory() * ramPercentage / valueSize);
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
            if (stringData) {
               String cacheData = new String(buffer);
               start = System.nanoTime();
               cacheWrapper.put(bucket, key, cacheData);
            } else {
               cacheWrapper.put(bucket, key, buffer);

            }
            if (printWriteStatistics) {
               log.info("Put on slave-" + getSlaveIndex() + " took "
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
         String punctuationChars = "!,.;?";

         ByteBuffer data = ByteBuffer.allocate(dataSize);
         while (data.remaining() > 0) {
            byte[] word;
            if (limitWordCount) {
               word = generateRandomWord(maxWordByteLength);
            } else {
               word = generateRandomUniqueWord(maxWordByteLength, true);
            }
            data = data.put(word);

            if (data.remaining() >= 2 && random.nextInt() % 5 == 0 && random.nextBoolean()) {
               data.put((byte) punctuationChars.charAt(random.nextInt(punctuationChars.length() - 1)));
               data.put((byte) '\n');
            } else {
               if (data.remaining() >= 1) {
                  data.put((byte) ' ');
               }
            }

            if (data.remaining() < maxWordByteLength) {
               maxWordByteLength = data.remaining();
            }
         }

         buffer = data.array();
      } else {
         buffer = new byte[dataSize];
         random.nextBytes(buffer);
      }
      return buffer;
   }

   /**
    * 
    * Randomly selects a random length word based on the words array defined above
    * 
    * @param maxBytes
    *           the maximum length in bytes of the word
    * @return the word as an array of bytes which may be less than maxBytes
    */
   private byte[] generateRandomWord(int maxBytes) {
      byte[] result = new byte[0];
      // Random.nextInt(0) generates an error
      if (maxBytes - 1 > 0) {
         int byteLength = random.nextInt(maxBytes - 1) + 1;
         String[] pickWords = words[byteLength - 1];
         int pick = 0;
         if (pickWords.length - 1 > 0) {
            pick = random.nextInt(pickWords.length - 1);
         }
         result = pickWords[pick].getBytes();
      }
      return result;
   }

   /**
    * 
    * Generates a random length "word" by randomly selecting single and multi-byte characters
    * 
    * @param maxBytes
    *           the maximum length in bytes of the word
    * @param randomLength
    *           if <code>true</code>, use a random length with a max value of <code>maxBytes</code>
    * @return the word as an array of bytes which may be less than maxBytes
    */
   private byte[] generateRandomUniqueWord(int maxBytes, boolean randomLength) {
      String singleByteChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
      String multiByteChars = "ÅÄÇÉÑÖÕÜàäâáãçëèêéîïìíñôöòóüûùúÿ";
      StringBuilder data = new StringBuilder();

      int byteLength = maxBytes;
      if (randomLength) {
         byteLength = random.nextInt(maxBytes - 1) + 1;
      }

      for (int i = byteLength; i > 0; i--) {
         if (i == 1) {
            data.append(singleByteChars.charAt(random.nextInt(singleByteChars.length() - 1)));
         } else {
            data.append(multiByteChars.charAt(random.nextInt(multiByteChars.length() - 1)));
            i--;
         }
      }

      return data.toString().getBytes();
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
      if (limitWordCount) {
         int totalWordCount = maxWordCount;
         if (!shareWords) {
            totalWordCount = maxWordCount * getActiveSlaveCount();
         }
         log.info(totalWordCount + " words were generated with a maximum length of " + maxWordByteLength + " bytes");

      }
      log.info("--------------------");
      return true;
   }
}
