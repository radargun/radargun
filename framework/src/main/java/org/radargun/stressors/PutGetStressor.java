package org.radargun.stressors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.CacheWrapperStressor;
import org.radargun.utils.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * On multiple threads executes put and get operations against the CacheWrapper, and returns the result as an Map.
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
   private int numberOfRequests = 50000;

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

   private String bucketPrefix = DEFAULT_BUCKET_PREFIX;


   private CacheWrapper cacheWrapper;
   private static Random r = new Random();
   private long startTime;
   private volatile CountDownLatch startPoint;


   public Map<String, String> stress(CacheWrapper wrapper) {
      this.cacheWrapper = wrapper;
      startTime = System.currentTimeMillis();
      log.info("Executing: " + this.toString());

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
      double requestPerSec = (reads + writes) / ((duration/numOfThreads) / 1000.0);
      results.put("REQ_PER_SEC", str(requestPerSec));
      results.put("READS_PER_SEC", str(reads / ((readsDurations/numOfThreads) / 1000.0)));
      results.put("WRITES_PER_SEC", str(writes / ((writesDurations/numOfThreads) / 1000.0)));
      results.put("READ_COUNT", str(reads));
      results.put("WRITE_COUNT", str(writes));
      results.put("FAILURES", str(failures));
      log.info("Finished generating report. Nr of failed operations on this node is: " + failures +
            ". Test duration is: " + Utils.getDurationString(System.currentTimeMillis() - startTime));
      return results;
   }

   private List<Stresser> executeOperations() throws Exception {
      List<Stresser> stressers = new ArrayList<Stresser>();
      final AtomicInteger requestsLeft = new AtomicInteger(numberOfRequests);
      startPoint = new CountDownLatch(1);
      for (int threadIndex = 0; threadIndex < numOfThreads; threadIndex++) {
         Stresser stresser = new Stresser(threadIndex, requestsLeft);
         stresser.initializeKeys();
         stressers.add(stresser);
         stresser.start();
      }
      log.info("Cache wrapper info is: " + cacheWrapper.getInfo());
      startPoint.countDown();
      for (Stresser stresser : stressers) {
         stresser.join();
      }
      return stressers;
   }

   private class Stresser extends Thread {

      private ArrayList<String> pooledKeys = new ArrayList<String>(numberOfKeys);

      private int threadIndex;
      private String bucketId;
      private int nrFailures;
      private long readDuration = 0;
      private long writeDuration = 0;
      private long reads;
      private long writes;
      private long startTime;
      private AtomicInteger requestsLeft;

      public Stresser(int threadIndex, AtomicInteger requestsLeft) {
         super("Stresser-" + threadIndex);
         this.threadIndex = threadIndex;
         bucketId = getBucketId(threadIndex);
         this.requestsLeft = requestsLeft;
      }

      @Override
      public void run() {
         startTime = System.currentTimeMillis();
         int readPercentage = 100 - writePercentage;
         Random r = new Random();
         String payload = generateRandomString(sizeOfValue);
         int randomAction;
         int randomKeyInt;
         try {
            startPoint.await();
            log.info("Starting thread: " + getName());
         } catch (InterruptedException e) {
            log.warn(e);
         }

         int i = 0;
         while(requestsLeft.getAndDecrement() > -1) {
            randomAction = r.nextInt(100);
            randomKeyInt = r.nextInt(numberOfKeys - 1);
            String key = getKey(randomKeyInt);
            Object result = null;

            if (randomAction < readPercentage) {
               long start = System.currentTimeMillis();
               try {
                  result = cacheWrapper.get(bucketId, key);
               } catch (Exception e) {
                  log.warn(e);
                  nrFailures++;
               }
               readDuration += System.currentTimeMillis() - start;
               reads++;
            } else {
               long start = System.currentTimeMillis();
               try {
                  cacheWrapper.put(bucketId, key, payload);
                  logProgress(i, null);
               } catch (Exception e) {
                  log.warn(e);
                  nrFailures++;
               }
               writeDuration += System.currentTimeMillis() - start;
               writes++;
            }
            i++;
            logProgress(i, result);
         }
      }


      private void logProgress(int i, Object result) {
         if ((i + 1) % opsCountStatusLog == 0) {
            double elapsedTime = System.currentTimeMillis() - startTime;
            double estimatedTotal = ((double) (numberOfRequests / numOfThreads) / (double) i) * elapsedTime;
            double estimatedRemaining = estimatedTotal - elapsedTime;
            if (log.isTraceEnabled()) {
               log.trace("i=" + i + ", elapsedTime=" + elapsedTime);
            }
            log.info("Thread index '" + threadIndex + "' executed " + (i + 1) + " operations. Elapsed time: " +
                  Utils.getDurationString((long) elapsedTime) + ". Estimated remaining: " + Utils.getDurationString((long) estimatedRemaining) +
                  ". Estimated total: " + Utils.getDurationString((long) estimatedTotal));
            System.out.println("Last result" + result);//this is printed here just to make sure JIT doesn't
            // skip the call to cacheWrapper.get
         }
      }

      public long totalDuration() {
         return readDuration + writeDuration;
      }

      public void initializeKeys() {
         for (int keyIndex = 0; keyIndex < numberOfKeys; keyIndex++) {
            try {
               String key = this.bucketId + "-" + this.threadIndex +  "::" + keyIndex;
               pooledKeys.add(key);
               cacheWrapper.put(this.bucketId, key, generateRandomString(sizeOfValue));
            }
            catch (Throwable e) {
               log.warn("Error while initializing the session: ", e);
            }
         }
      }

      public String getKey(int keyIndex) {
         return pooledKeys.get(keyIndex);
      }
   }

   private String str(Object o) {
      return String.valueOf(o);
   }

   public void setNumberOfRequests(int numberOfRequests) {
      this.numberOfRequests = numberOfRequests;
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
    * This will make sure that each session runs in its own thread and no collision will take place. See
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
            ", numberOfRequests=" + numberOfRequests +
            ", numberOfKeys=" + numberOfKeys +
            ", sizeOfValue=" + sizeOfValue +
            ", writePercentage=" + writePercentage +
            ", numOfThreads=" + numOfThreads +
            ", bucketPrefix=" + bucketPrefix +
            ", cacheWrapper=" + cacheWrapper +
            "}";
   }
}

