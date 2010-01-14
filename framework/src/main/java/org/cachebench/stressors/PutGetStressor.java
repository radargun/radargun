package org.cachebench.stressors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.CacheWrapperStressor;
import org.cachebench.utils.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static java.lang.Integer.MAX_VALUE;

/**
 * On multiple threads executes put and get opperations against the CacheWrapper, and returns the result as an Map.
 *
 * @author Mircea.Markus@jboss.com
 */
public class PutGetStressor implements CacheWrapperStressor {

   private static Log log = LogFactory.getLog(PutGetStressor.class);

   private int opsCountStatusLog = 5000;

   public static final String DEFAULT_BUCKET_PREFIX = "BUCKET_";
   public static final String DEFAULT_KEY_PREFIX = "KEY_";


   /**
    * total number of operation to be made against cache wrapper: reads + writes
    */
   private int numberOfRequestsPerThread = 50000;

   /**
    * for each there will be created fixed number of keys. All the GETs and PUTs are performed on these keys only.
    */
   private int numberOfKeys = 100;

   /**
    * Each key will be a byte[] of this size.
    */
   private int sizeOfValue = 1000;

   /**
    * Out of the total number of operations, this defines the frequency of writes (percentage).
    */
   private int writePercentage = 20;


   /**
    * the number of threads that will work on this cache wrapper.
    */
   private int numOfThreads = 10;

   private String keyPrefix = DEFAULT_KEY_PREFIX;

   private String bucketPrefix = DEFAULT_BUCKET_PREFIX;


   private CacheWrapper cacheWrapper;
   private static Random r = new Random();
   private long startTime;
   private volatile CountDownLatch startPoint;


   public Map<String, String> stress(CacheWrapper wrapper) {
      this.cacheWrapper = wrapper;
      startTime = System.currentTimeMillis();
      log.info("Executing: " + this.toString());

      initBuckets();

      List<Stresser> stressers;
      try {
         stressers = executeOperations();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      return processResults(stressers);
   }

   public void destroy() throws Exception {
      cacheWrapper.empty();
      cacheWrapper = null;
   }

   private Map<String, String> processResults(List<Stresser> stressers) {
      long duration = 0;
      int reads = 0;
      int writes = 0;
      int failures = 0;
      long readsDurations = 0;
      long writesDurations = 0;

      for (Stresser stresser : stressers) {
         duration += stresser.totalDuration();
         readsDurations += stresser.readDuration;
         writesDurations += stresser.writeDuration;

         reads += stresser.reads;
         writes += stresser.writes;
         failures += stresser.nrFailures;
      }

      Map<String, String> results = new LinkedHashMap<String, String>();
      results.put("DURATION", str(duration));
      double requestPerSec = (reads + writes) / (duration / 1000.0);
      results.put("REQ_PER_SEC", str(requestPerSec));
      results.put("READS_PER_SEC", str(reads / (readsDurations / 1000.0)));
      results.put("WRITES_PER_SEC", str(writes / (writesDurations / 1000.0)));
      results.put("READ_COUNT", str(reads));
      results.put("WRITE_COUNT", str(writes));
      results.put("FAILURES", str(failures));
      log.info("Finished generating report. Nr of failed operations on this node is: " + failures +
            ". Test duration is: " + Utils.getDurationString(System.currentTimeMillis() - startTime));
      return results;
   }

   private List<Stresser> executeOperations() throws Exception {
      List<Stresser> stressers = new ArrayList<Stresser>();
      startPoint = new CountDownLatch(1);
      for (int threadIndex = 0; threadIndex < numOfThreads; threadIndex++) {
         Stresser stresser = new Stresser(threadIndex);
         stressers.add(stresser);
         stresser.start();
      }
      startPoint.countDown();
      for (Stresser stresser : stressers) {
         stresser.join();
      }
      return stressers;
   }

   private void initBuckets() {
      for (int threadIndex = 0; threadIndex < numOfThreads; threadIndex++) {
         for (int keyIndex = 0; keyIndex < numberOfKeys; keyIndex++) {
            try {
               cacheWrapper.put(getBucketId(threadIndex), getKey(keyIndex), generateRandomString(sizeOfValue));
            }
            catch (Throwable e) {
               log.warn("Error while initializing the session: ", e);
            }
         }
      }
   }

   private String getKey(int keyIndex) {
      return Integer.toString(r.nextInt(MAX_VALUE), 36) + "_" + keyPrefix + '_' + keyIndex;
   }


   public class Stresser extends Thread {

      private int threadIndex;
      private String bucketId;
      private int nrFailures;
      private long readDuration = 0;
      private long writeDuration = 0;
      private long reads;
      private long writes;
      private long startTime;

      public Stresser(int threadIndex) {
         super("Stresser-" + threadIndex);
         this.threadIndex = threadIndex;
         bucketId = getBucketId(threadIndex);
      }

      @Override
      public void run() {
         startTime = System.currentTimeMillis();
         int readPercentage = 100 - writePercentage;
         Random r = new Random();
         int randomAction;
         int randomKeyInt;
         try {
            startPoint.await();
            log.info("Starting thread: " + getName());
         } catch (InterruptedException e) {
            log.warn(e);
         }
         for (int i = 0; i < numberOfRequestsPerThread; i++) {
            logProgress(i);
            randomAction = r.nextInt(100);
            randomKeyInt = r.nextInt(numberOfKeys - 1);
            String key = getKey(randomKeyInt);

            if (randomAction < readPercentage) {
               long start = System.currentTimeMillis();
               Object result = null;
               try {
                  result = cacheWrapper.get(bucketId, key);
               } catch (Exception e) {
                  log.warn(e);
                  nrFailures++;
               }
               readDuration += System.currentTimeMillis() - start;
               reads++;
               makeSureCallIsNotSkipped(result);
            } else {
               String payload = generateRandomString(sizeOfValue);
               long start = System.currentTimeMillis();
               try {
                  cacheWrapper.put(bucketId, key, payload);
               } catch (Exception e) {
                  log.warn(e);
                  nrFailures++;
               }
               writeDuration += System.currentTimeMillis() - start;
               writes++;
            }
         }
      }

      /**
       * Just to make sure that compiler won't ignore the call to get.
       */
      public void makeSureCallIsNotSkipped(Object result) {
         if (result != null && result.hashCode() < System.currentTimeMillis()) {
            System.out.println("");
         }
      }

      private void logProgress(int i) {
         if ((i + 1) % opsCountStatusLog == 0) {
            double elapsedTime = System.currentTimeMillis() - startTime;
            double estimatedTotal = ((double) numberOfRequestsPerThread / (double) i) * elapsedTime;
            double estimatedRemaining = estimatedTotal - elapsedTime;
            if (log.isTraceEnabled()) {
               log.trace("i=" + i + ", elapsedTime=" + elapsedTime);
            }
            log.info("Thread index '" + threadIndex + "' executed " + (i + 1) + " operations. Elapsed time: " +
                  Utils.getDurationString((long) elapsedTime) + ". Estimated remaining: " + Utils.getDurationString((long) estimatedRemaining) +
                  ". Estimated total: " + Utils.getDurationString((long) estimatedTotal));
         }
      }

      public long totalDuration() {
         return readDuration + writeDuration;
      }
   }

   private String str(Object o) {
      return String.valueOf(o);
   }

   public void setNumberOfRequestsPerThread(int numberOfRequestsPerThread) {
      this.numberOfRequestsPerThread = numberOfRequestsPerThread;
   }

   public void setNumberOfAttributes(int numberOfKeys) {
      this.numberOfKeys = numberOfKeys;
   }

   public void setSizeOfAnAttribute(int sizeOfValue) {
      this.sizeOfValue = sizeOfValue;
   }

   public void setNumOfThreads(int numOfThreads) {
      this.numOfThreads = numOfThreads;
   }

   public void setWritePercentage(int writePercentage) {
      this.writePercentage = writePercentage;
   }

   public void setOpsCountStatusLog(int opsCountStatusLog) {
      this.opsCountStatusLog = opsCountStatusLog;
   }

   /**
    * This will make sure that each session runs in its own thread and no collisition will take place. See
    * https://sourceforge.net/apps/trac/cachebenchfwk/ticket/14
    */
   private String getBucketId(int threadIndex) {
      return bucketPrefix + "_" + threadIndex;
   }

   private static String generateRandomString(int size) {
      // each char is 2 bytes
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < size / 2; i++) sb.append((char) (64 + r.nextInt(26)));
      return sb.toString();
   }

   public String getKeyPrefix() {
      return keyPrefix;
   }

   public void setKeyPrefix(String keyPrefix) {
      this.keyPrefix = keyPrefix;
   }

   public String getBucketPrefix() {
      return bucketPrefix;
   }

   public void setBucketPrefix(String bucketPrefix) {
      this.bucketPrefix = bucketPrefix;
   }

   @Override
   public String toString() {
      return "PutGetStressor{" +
            "opsCountStatusLog=" + opsCountStatusLog +
            ", numberOfRequestsPerThread=" + numberOfRequestsPerThread +
            ", numberOfKeys=" + numberOfKeys +
            ", sizeOfValue=" + sizeOfValue +
            ", writePercentage=" + writePercentage +
            ", numOfThreads=" + numOfThreads +
            ", bucketPrefix=" + bucketPrefix +
            ", keyPrefix=" + keyPrefix +
            ", cacheWrapper=" + cacheWrapper +
            "}";
   }
}
