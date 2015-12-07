package org.radargun.stages.cache;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.stats.DefaultOperationStats;
import org.radargun.stats.DefaultStatistics;
import org.radargun.stats.Statistics;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.InjectTrait;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * <p>
 * Generates random data to fill the cache. The seed used to instantiate the java.util.Random object
 * can be specified, so that the same data is generated between runs. To specify generating a fixed
 * amount of data in the cache, specify the valueSize and valueCount parameters. The number of
 * values will be divided by the return value of <code>getActiveSlaveCount()</code>. To generate
 * data based on the amount of available RAM, specify the valueSize and ramPercentage parameters.
 * The amount of free memory on each node will be calculated and then used to determine the number
 * of values that are written by the node.
 * </p>
 *
 * <p>
 * To add a precise amount of data to the cache, you need to be aware of the storage overhead. For a
 * byte array, each value needs an additional 152 bytes. When <code>stringData</code> is enabled,
 * the values will require 2 * <code>valueSize</code> bytes + the additional 152 bytes. Keep these
 * values in mind when calculating the <code>valueCount</code>.
 * </p>
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Generates random data to fill the cache.")
public class RandomDataStage extends AbstractDistStage {

   public static final String RANDOMDATA_TOTALBYTES_KEY = "randomDataTotalBytes";
   public static final String RANDOMDATA_CLUSTER_WORDCOUNT_KEY = "randomDataClusterWordcount";

   @Property(doc = "The seed to use for the java.util.Random object. "
      + "The default is the return value of Calendar.getInstance().getWeekYear().")
   public long randomSeed = Calendar.getInstance().getWeekYear();

   @Property(doc = "The size of the values to put into the cache. The default size is 1MB (1024 * 1024).")
   public int valueSize = 1024 * 1024;

   @Property(doc = "The number of values of valueSize to write to the cache. "
      + "Either valueCount or ramPercentageDataSize should be specified, but not both.")
   public long valueCount = -1;

   @Property(doc = "A double that represents the percentage of the total Java heap "
      + "used to determine the amount of data to put into the cache. "
      + "Either valueCount or ramPercentageDataSize should be specified, but not both.")
   public double ramPercentage = -1;

   @Property(doc = "The name of the bucket where keys are written. The default is null.")
   public String bucket = null;

   @Property(doc = "If true, then String objects with printable characters are written to the cache."
      + "The default is false")
   public boolean stringData = false;

   @Property(doc = "If true, then the time for each put operation is written to the logs. The default is false.")
   public boolean printWriteStatistics = false;

   @Property(doc = "If true, then the random word generator selects a word from a pre-defined list. "
      + "The default is false.")
   public boolean limitWordCount = false;

   @Property(doc = "The maximum number of words to generate in the pre-defined list of words used with limitWordCount."
      + "The default is 100.")
   public int maxWordCount = 100;

   @Property(doc = "The maximum number of characters allowed in a word. The default is 20.")
   public int maxWordLength = 20;

   @Property(doc = "If false, then each node in the cluster generates a list of maxWordCount words. "
      + "If true, then each node in the cluster shares the same list of words. The default is false.")
   public boolean shareWords = false;

   /*
    * From http://infinispan.blogspot.com/2013/01/infinispan-memory-overhead.html and
    * http://infinispan.blogspot.com/2013/07/lower-memory-overhead-in-infinispan.html
    */
   @Property(doc = "The bytes used over the size of the key and value when "
      + "putting to the cache. By default the stage retrieves the value from cache wrapper automatically.")
   public int valueByteOverhead = -1;

   @Property(doc = "The number of bytes to write to the cache when the valueByteOverhead, "
      + "stringData, and valueSize are taken into account. The code assumes this is an "
      + "even multiple of valueSize plus valueByteOverhead. If stringData is true, then "
      + "the code assumes this is an even multiple of (2 * valueSize) plus valueByteOverhead.")
   public long targetMemoryUse = -1;

   @Property(doc = "The number of times to retry a put if it fails. Default is 10.")
   public int putRetryCount = 10;

   @Property(doc = "The maximum number of seconds to sleep before retrying a failed put command. The default is 5.")
   public int maxSleepInterval = 5;

   @Property(doc = "Size of batch to be loaded into cache (using putAll). If <= 0, put() operation is used sequentially. Default is 0.")
   public int batchSize = 0;

   @Property(doc = "Controls whether batch insertion is performed in asychronous way. Default is false (prefer synchronous operations).")
   public boolean useAsyncBatchLoading = false;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected BasicOperations basicOperations;

   @InjectTrait(dependency = InjectTrait.Dependency.OPTIONAL)
   protected BulkOperations bulkOperations;

   @InjectTrait
   protected CacheInformation cacheInformation;

   private Random random;
   private String[][] words = null;
   private Runtime runtime = null;
   private int newlinePunctuationModulo = 10;
   private long nodePutCount;
   private long countOfWordsInData = 0;
   private HashMap<String, Integer> wordCount = new HashMap<String, Integer>();

   /**
    *
    * Fills a multi-dimensional array with randomly generated words. The first dimension of the
    * array is based on the length of the word in characters, and runs from 1 to maxWordLength.
    * Dividing the wordCount by maxWordLength determines how many words of each length are
    * generated.
    *
    * @param wordCount
    *           the total number of words to generate
    * @param maxWordLength
    *           the maximum size in characters for a word
    */
   private void fillWordArray(int wordCount, int maxWordLength) {
      int wordsPerLength = wordCount / maxWordLength;
      words = new String[maxWordLength][wordsPerLength];
      for (int i = 1; i <= maxWordLength; i++) {
         for (int j = 0; j < wordsPerLength; j++) {
            /*
             * Intern the string to reduce memory usage since these words will be used multiple
             * times
             */
            words[i - 1][j] = new String(generateRandomUniqueWord(i, false)).intern();
         }
      }
      if (log.isTraceEnabled()) {
         log.tracef("Slave %d words array = %s", slaveState.getSlaveIndex(), Arrays.deepToString(words));
      }
   }

   @Override
   public void initOnSlave(SlaveState slaveState) {
      super.initOnSlave(slaveState);

      if (shareWords && limitWordCount) {
         random = new Random(randomSeed);
      } else {
         /*
          * Add the slaveIndex to the seed to guarantee that each node generates a different word
          * list
          */
         random = new Random(randomSeed + slaveState.getSlaveIndex());
      }
      fillWordArray(maxWordCount, maxWordLength);
   }

   @Override
   public DistStageAck executeOnSlave() {
      random = new Random(randomSeed + slaveState.getSlaveIndex());

      if (ramPercentage > 0 && valueCount > 0) {
         return errorResponse("Either valueCount or ramPercentageDataSize should be specified, but not both");
      }

      if (shareWords && !limitWordCount) {
         return errorResponse("The shareWords property can only be true when limitWordCount is also true.");
      }

      if (limitWordCount && !stringData) {
         return errorResponse("The limitWordCount property can only be true when stringData is also true.");
      }

      if (valueByteOverhead == -1 && cacheInformation == null) {
         return errorResponse("The valueByteOverhead property must be supplied for this cache.");
      }

      if (batchSize > 0 && bulkOperations == null) {
         return errorResponse("Batch loading was enabled, however service doesn't provide " + BulkOperations.class + " implementation");
      }

      /*
       * If valueByteOverhead is not specified, then try to retrieve the byte overhead from the
       * CacheWrapper
       */
      if (valueByteOverhead == -1 && cacheInformation != null) {
         valueByteOverhead = cacheInformation.getCache(null).getEntryOverhead();
      }

      runtime = Runtime.getRuntime();
      int valueSizeWithOverhead = valueByteOverhead;
      /*
       * String data is twice the size of a byte array
       */
      if (stringData) {
         valueSizeWithOverhead += (valueSize * 2);
      } else {
         valueSizeWithOverhead += valueSize;
      }

      if (ramPercentage > 0) {
         System.gc();
         targetMemoryUse = (long) (runtime.maxMemory() * ramPercentage);
         log.trace("targetMemoryUse: " + Utils.kbString(targetMemoryUse));

         nodePutCount = (long) Math.ceil(targetMemoryUse / valueSizeWithOverhead);
      } else {
         long totalPutCount = valueCount;
         if (targetMemoryUse > 0) {
            if (targetMemoryUse % valueSizeWithOverhead != 0) {
               log.warn("The supplied value for targetMemoryUse (" + targetMemoryUse
                  + ") is not evenly divisible by the value size plus byte overhead (" + valueSizeWithOverhead + ")");
            }
            totalPutCount = targetMemoryUse / valueSizeWithOverhead;
         }
         nodePutCount = (long) Math.ceil(totalPutCount / slaveState.getClusterSize());
         /*
          * Add one to the nodeCount on each slave with an index less than the remainder so that the
          * correct number of values are written to the cache
          */
         if ((totalPutCount % slaveState.getClusterSize() != 0)
            && slaveState.getSlaveIndex() < (totalPutCount % slaveState.getClusterSize())) {
            nodePutCount++;
         }
      }

      long putCount = nodePutCount;
      boolean useBatchLoading = batchSize > 0;
      long bytesWritten = 0;
      int numOperationsToPerform = 0;

      BulkOperations.Cache<String, Object> bulkOperationsCache = null;
      BasicOperations.Cache<String, Object> basicOperationsCache = null;

      if (useBatchLoading) {
         bulkOperationsCache = bulkOperations.getCache(bucket, useAsyncBatchLoading);
         numOperationsToPerform = batchSize;
      } else {
         basicOperationsCache = basicOperations.getCache(bucket);
         numOperationsToPerform = 1;
      }

      try {
         byte[] buffer = new byte[valueSize];
         Statistics stats = new DefaultStatistics(new DefaultOperationStats());
         stats.begin();
         int counter = 0;
         Map map = new HashMap(numOperationsToPerform);
         while (putCount > 0) {
            String key = Integer.toString(slaveState.getSlaveIndex()) + "-" + putCount + ":" + TimeService.nanoTime();

            long start = -1;

            String cacheData = null;

            if (stringData) {
               cacheData = generateRandomStringData(valueSize);
               if (useBatchLoading) {
                  map.put(key, cacheData);
               }
            } else {
               random.nextBytes(buffer);
               if (useBatchLoading) {
                  map.put(key, buffer);
               }
            }

            // putCount == 1 -> last iteration
            if (counter == numOperationsToPerform - 1 || putCount == 1) {
               boolean success = false;
               for (int i = 0; i < putRetryCount; ++i) {
                  try {
                     if (stringData) {
                        if (putCount % 5000 == 0) {
                           log.info(i + ": Writing string length " + valueSize + " to cache key: " + key);
                        }

                        start = TimeService.nanoTime();
                        if (batchSize > 0) {
                           bulkOperationsCache.putAll(map);
                        } else {
                           basicOperationsCache.put(key, cacheData);
                        }
                     } else {
                        if (putCount % 5000 == 0) {
                           log.info(i + ": Writing " + valueSize + " bytes to cache key: " + key);
                        }

                        start = TimeService.nanoTime();
                        if (batchSize > 0) {
                           bulkOperationsCache.putAll(map);
                        } else {
                           basicOperationsCache.put(key, buffer);
                        }
                     }
                     long durationNanos = TimeService.nanoTime() - start;
                     stats.registerRequest(durationNanos, BasicOperations.PUT);
                     if (printWriteStatistics) {
                        log.info("Put on slave" + slaveState.getSlaveIndex() + " took "
                           + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
                     }
                     success = true;
                     counter = 0;
                     map.clear();
                     break;
                  } catch (Exception e) {
                     // If the put fails, sleep to see if staggering the put will succeed
                     Thread.sleep(maxSleepInterval * 1000);
                  }
               }
               if (!success) {
                  return errorResponse("Failed to insert entry into cache",
                     new RuntimeException(String.format("Failed to insert entry %d times.", putRetryCount)));
               }
            }

            if (stringData) {
               bytesWritten += (valueSize * 2);
            } else {
               bytesWritten += valueSize;
            }

            putCount--;
            counter++;
         }
         stats.end();
         System.gc();
         log.info("Memory - free: " + Utils.kbString(runtime.freeMemory()) + " - max: "
            + Utils.kbString(runtime.maxMemory()) + "- total: " + Utils.kbString(runtime.totalMemory()));
         log.debug("nodePutCount = " + nodePutCount + "; bytesWritten = " + bytesWritten + "; targetMemoryUse = "
            + targetMemoryUse + "; countOfWordsInData = " + countOfWordsInData);
         return new DataInsertAck(slaveState, nodePutCount, cacheInformation.getCache(null).getLocallyStoredSize(),
            bytesWritten, targetMemoryUse, countOfWordsInData, wordCount, stats);
      } catch (Exception e) {
         return errorResponse("An exception occurred", e);
      } finally {
         // Log the word counts for this node
         if (stringData && !wordCount.isEmpty() && log.isDebugEnabled()) {
            log.debug("Word counts for node" + slaveState.getSlaveIndex());
            log.debug("--------------------");
            for (Map.Entry<String, Integer> entry : wordCount.entrySet()) {
               log.debug("key: " + entry.getKey() + "; value: " + entry.getValue());
            }
            log.debug("--------------------");
         }
      }
   }

   private String generateRandomStringData(int dataSize) {
      /*
       * Generate a random string of "words" using random single and multi-byte characters that are
       * separated by punctuation marks and whitespace.
       */
      String punctuationChars = "!,.;?";
      int wordLength = maxWordLength;

      CharBuffer data = CharBuffer.allocate(dataSize);
      while (data.remaining() > 0) {
         String word;
         if (limitWordCount) {
            word = pickRandomWord(wordLength);
         } else {
            word = generateRandomUniqueWord(wordLength, true);
         }
         data = data.put(word);
         countOfWordsInData++;
         if (wordCount.containsKey(word)) {
            wordCount.put(word, wordCount.get(word) + 1);
         } else {
            wordCount.put(word, 1);
         }

         if (data.remaining() >= 2 && random.nextInt() % newlinePunctuationModulo == 0) {
            data.put(punctuationChars.charAt(random.nextInt(punctuationChars.length() - 1)));
            data.put('\n');
         } else {
            if (data.remaining() >= 1) {
               data.put(' ');
            }
         }

         if (data.remaining() < wordLength) {
            wordLength = data.remaining();
         }
      }

      return data.rewind().toString();
   }

   /**
    *
    * Randomly selects a random length word based on the words array defined above
    *
    * @param maxLength
    *           the maximum length of the word
    * @return the word as a String whose length may be less than <code>maxLength</code>
    */
   private String pickRandomWord(int maxLength) {
      String word = "";
      String[] pickWords = {};
      int pick = 0;
      int wordLength = maxLength;
      // Random.nextInt(0) generates an error
      if (maxLength - 1 > 0) {
         wordLength = random.nextInt(maxLength - 1) + 1;
      }
      pickWords = words[wordLength - 1];
      if (pickWords.length - 1 > 0) {
         pick = random.nextInt(pickWords.length - 1);
      }
      word = pickWords[pick];
      return word;
   }

   /**
    *
    * Generates a random length "word" by randomly selecting single and multi-byte characters
    *
    * @param maxLength
    *           the maximum length of the word
    * @param randomLength
    *           if <code>true</code>, use a random length with a max value of <code>maxLength</code>
    * @return the word as a String whose length may be less than <code>maxLength</code>
    */
   private String generateRandomUniqueWord(int maxLength, boolean randomLength) {
      String singleByteChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
      String multiByteChars = "ÅÄÇÉÑÖÕÜàäâáãçëèêéîïìíñôöòóüûùúÿ";
      StringBuilder data = new StringBuilder();

      int wordLength = maxLength;
      if (randomLength && maxLength - 1 > 0) {
         wordLength = random.nextInt(maxLength - 1) + 1;
      }

      for (int i = wordLength; i > 0; i--) {
         // If wordLength == 1, then only use singleByteChars
         if (wordLength > 1 && random.nextBoolean()) {
            data.append(multiByteChars.charAt(random.nextInt(multiByteChars.length() - 1)));
         } else {
            data.append(singleByteChars.charAt(random.nextInt(singleByteChars.length() - 1)));
         }
      }

      return data.toString();
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError())
         return result;

      log.info("--------------------");
      if (ramPercentage > 0) {
         if (stringData) {
            log.info("Filled cache with String objects totaling " + Math.round(ramPercentage * 100)
               + "% of the Java heap");
         } else {
            log.info("Filled cache with byte arrays totaling " + Math.round(ramPercentage * 100) + "% of the Java heap");
         }
      }
      if (ramPercentage < 0 && targetMemoryUse > 0) {
         if (stringData) {
            log.info("Filled cache with String objects totaling " + Utils.kbString(targetMemoryUse));
         } else {
            log.info("Filled cache with byte arrays totaling " + Utils.kbString(targetMemoryUse));
         }
      }
      if (valueCount > 0) {
         if (stringData) {
            log.info("Filled cache with " + Utils.kbString((valueSize * 2) * valueCount) + " of " + valueSize
               + " character String objects");
         } else {
            log.info("Filled cache with " + Utils.kbString(valueSize * valueCount) + " of " + Utils.kbString(valueSize)
               + " byte arrays");
         }
      }

      Report report = masterState.getReport();
      Report.Test test = report.createTest("Random_Data_Stage", null, true);
      int testIteration = test.getIterations().size();

      Map<Integer, Report.SlaveResult> nodeKeyCountsResult = new HashMap<Integer, Report.SlaveResult>();
      Map<Integer, Report.SlaveResult> nodeTargetMemoryUseResult = new HashMap<Integer, Report.SlaveResult>();
      Map<Integer, Report.SlaveResult> nodeCountOfWordsInDataResult = new HashMap<Integer, Report.SlaveResult>();
      Map<Integer, Report.SlaveResult> nodeBytesWritten = new HashMap<Integer, Report.SlaveResult>();

      long totalValues = 0;
      long totalBytes = 0;
      long totalNodeWordCount = 0;
      Map<String, Integer> clusterWordCount = new TreeMap<String, Integer>();
      for (DataInsertAck ack : instancesOf(acks, DataInsertAck.class)) {
         if (ack.wordCount != null) {
            for (Map.Entry<String, Integer> entry : ack.wordCount.entrySet()) {
               if (clusterWordCount.containsKey(entry.getKey())) {
                  clusterWordCount.put(entry.getKey(), clusterWordCount.get(entry.getKey()) + entry.getValue());
               } else {
                  clusterWordCount.put(entry.getKey(), entry.getValue());
               }
            }
         }

         nodeKeyCountsResult.put(ack.getSlaveIndex(), new Report.SlaveResult(Long.toString(ack.nodeKeyCount), false));
         nodeBytesWritten.put(ack.getSlaveIndex(), new Report.SlaveResult(Long.toString(ack.bytesWritten), false));
         test.addStatistics(testIteration, ack.getSlaveIndex(), Collections.singletonList(ack.nodePutStats));

         totalValues += ack.nodePutCount;
         totalBytes += ack.bytesWritten;
         String logInfo = "Slave " + ack.getSlaveIndex() + " wrote " + ack.nodePutCount
            + " values to the cache with a total size of " + Utils.kbString(ack.bytesWritten);
         if (ramPercentage > 0) {
            logInfo += "; targetMemoryUse = " + Utils.kbString(ack.targetMemoryUse);
            nodeTargetMemoryUseResult.put(ack.getSlaveIndex(), new Report.SlaveResult(Long.toString(ack.targetMemoryUse), false));
         }
         if (stringData) {
            logInfo += "; countOfWordsInData = " + ack.countOfWordsInData;
            nodeCountOfWordsInDataResult.put(ack.getSlaveIndex(), new Report.SlaveResult(Long.toString(ack.countOfWordsInData), false));
         }
         log.info(logInfo);
      }
      log.info("The cache contains " + totalValues + " values with a total size of " + Utils.kbString(totalBytes));
      if (limitWordCount) {
         int totalWordCount = maxWordCount;
         if (!shareWords) {
            totalWordCount = maxWordCount * slaveState.getClusterSize();
         }
         log.info(totalWordCount + " words were generated with a maximum length of " + maxWordLength + " characters");
      }
      if (!clusterWordCount.isEmpty() && log.isDebugEnabled()) {
         log.debug("--------------------");
         log.debug("Cluster wide word count:");
         for (String key : clusterWordCount.keySet()) {
            log.debug("word: " + key + "; count: " + clusterWordCount.get(key));
         }
         //TODO Will this take too much memory?
         //         masterState.put(RANDOMDATA_CLUSTER_WORDCOUNT_KEY, clusterWordCount);
      }
      log.info("--------------------");

      masterState.put(RANDOMDATA_TOTALBYTES_KEY, totalBytes);
      test.addResult(
         testIteration,
         new Report.TestResult("Kilobytes written per node", nodeBytesWritten, Utils.kbString(totalBytes), false));

      test.addResult(testIteration, new Report.TestResult("Key count per node", nodeKeyCountsResult, "", false));
      if (!nodeTargetMemoryUseResult.isEmpty()) {
         test.addResult(testIteration, new Report.TestResult("Target memory use per node", nodeTargetMemoryUseResult,
            Utils.kbString(totalBytes), false));
      }
      if (!nodeCountOfWordsInDataResult.isEmpty()) {
         test.addResult(testIteration, new Report.TestResult("Count of words in data per node",
            nodeCountOfWordsInDataResult, Long.toString(totalNodeWordCount), false));
      }

      return StageResult.SUCCESS;
   }

   private static class DataInsertAck extends DistStageAck {
      final long nodePutCount;
      final long nodeKeyCount;
      final long bytesWritten;
      final long targetMemoryUse;
      final long countOfWordsInData;
      final Map<String, Integer> wordCount;
      final Statistics nodePutStats;

      private DataInsertAck(SlaveState slaveState, long nodePutCount, long nodeKeyCount, long bytesWritten,
                            long targetMemoryUse, long countOfWordsInData, Map<String, Integer> wordCount, Statistics nodePutStats) {
         super(slaveState);
         this.nodePutCount = nodePutCount;
         this.nodeKeyCount = nodeKeyCount;
         this.bytesWritten = bytesWritten;
         this.targetMemoryUse = targetMemoryUse;
         this.countOfWordsInData = countOfWordsInData;
         this.wordCount = wordCount;
         this.nodePutStats = nodePutStats;
      }
   }
}
