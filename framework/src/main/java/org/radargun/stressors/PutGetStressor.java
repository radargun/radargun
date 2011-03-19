package org.radargun.stressors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
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
public class PutGetStressor extends AbstractCacheWrapperStressor {

   private static Log log = LogFactory.getLog(PutGetStressor.class);

   private int opsCountStatusLog = 5000;

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

   /**
    * This node's index in the Radargun cluster.  -1 is used for local benchmarks.
    */
   private int nodeIndex = -1;

   private int transactionSize = 1;

   private boolean useTransactions = false;

   private boolean commitTransactions = true;

   private AtomicInteger txCount = new AtomicInteger(0);

   private String keyGeneratorClass = StringKeyGenerator.class.getName();

   private KeyGenerator keyGenerator;


   private CacheWrapper cacheWrapper;
   private static Random r = new Random();
   private long startNanos;
   private volatile CountDownLatch startPoint;


   public Map<String, String> stress(CacheWrapper wrapper) {
      this.cacheWrapper = wrapper;
      startNanos = System.nanoTime();
      log.info("Executing: " + this.toString());

      List<Stressor> stressors;
      try {
         stressors = executeOperations();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      return processResults(stressors);
   }

   public void destroy() throws Exception {
      cacheWrapper.empty();
      cacheWrapper = null;
   }

   private Map<String, String> processResults(List<Stressor> stressors) {
      long duration = 0;
      long transactionDuration = 0;
      int reads = 0;
      int writes = 0;
      int failures = 0;
      long readsDurations = 0;
      long writesDurations = 0;

      for (Stressor stressor : stressors) {
         duration += stressor.totalDuration();
         readsDurations += stressor.readDuration;
         writesDurations += stressor.writeDuration;
         transactionDuration += stressor.getTransactionsDuration();

         reads += stressor.reads;
         writes += stressor.writes;
         failures += stressor.nrFailures;
      }

      Map<String, String> results = new LinkedHashMap<String, String>();
      results.put("DURATION", str(duration));
      double requestPerSec = (reads + writes) / ((duration/numOfThreads) / 1000000000.0);
      results.put("REQ_PER_SEC", str(requestPerSec));
      results.put("READS_PER_SEC", str(reads / ((readsDurations/numOfThreads) / 1000000000.0)));
      results.put("WRITES_PER_SEC", str(writes / ((writesDurations/numOfThreads) / 1000000000.0)));
      results.put("READ_COUNT", str(reads));
      results.put("WRITE_COUNT", str(writes));
      results.put("FAILURES", str(failures));
      if (useTransactions) {
         double txPerSec = txCount.get() / ((transactionDuration / numOfThreads) / 1000.0);
         results.put("TX_PER_SEC", str(txPerSec));
      }
      log.info("Finished generating report. Nr of failed operations on this node is: " + failures +
            ". Test duration is: " + Utils.getNanosDurationString(System.nanoTime() - startNanos));
      return results;
   }

   private List<Stressor> executeOperations() throws Exception {
      List<Stressor> stressors = new ArrayList<Stressor>(numOfThreads);
      final AtomicInteger requestsLeft = new AtomicInteger(numberOfRequests);
      startPoint = new CountDownLatch(1);
      for (int threadIndex = 0; threadIndex < numOfThreads; threadIndex++) {
         Stressor stressor = new Stressor(threadIndex, requestsLeft);
         stressor.initialiseKeys();
         stressors.add(stressor);
         stressor.start();
      }
      log.info("Cache wrapper info is: " + cacheWrapper.getInfo());
      startPoint.countDown();
      for (Stressor stressor : stressors) {
         stressor.join();
      }
      return stressors;
   }

   private boolean isLocalBenchmark() {
      return nodeIndex == -1;
   }

   private class Stressor extends Thread {

      private ArrayList<Object> pooledKeys = new ArrayList<Object>(numberOfKeys);

      private int threadIndex;
      private int nrFailures;
      private long readDuration = 0;
      private long writeDuration = 0;
      private long transactionDuration = 0;
      private long reads;
      private long writes;
      private long startNanos;
      private AtomicInteger requestsLeft;
      private final String bucketId;
      boolean txNotCompleted = false;

      public Stressor(int threadIndex, AtomicInteger requestsLeft) {
         super("Stressor-" + threadIndex);
         this.threadIndex = threadIndex;
         this.requestsLeft = requestsLeft;
         this.bucketId = isLocalBenchmark() ? String.valueOf(threadIndex) : nodeIndex + "_" + threadIndex;
      }

      @Override
      public void run() {
         try {
            runInternal();
         } catch (Exception e) {
            log.error("Unexpected error in stressor!", e);
         }
      }

      private void runInternal() {
         startNanos = System.nanoTime();
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

         int i = 0;
         while (requestsLeft.getAndDecrement() > -1) {
            randomAction = r.nextInt(100);
            randomKeyInt = r.nextInt(numberOfKeys - 1);
            Object key = getKey(randomKeyInt);
            Object result = null;

            if (randomAction < readPercentage) {
               result = doRead(key, i);
            } else {
               String payload = generateRandomString(sizeOfValue);
               doWrite(key, payload, i);
            }

            i++;
            logProgress(i, result);
         }

         if (txNotCompleted) {
            long start = System.nanoTime();
            completeTransaction(-1, true);
            transactionDuration += System.nanoTime() - start;
         }
      }

      private long startTx(int iteration) {
         long start = System.nanoTime();
         txNotCompleted = startTransaction(iteration);
         long txStartTime = 0;
         if (txNotCompleted) { //if a transaction has been started add the starting time
            txStartTime = System.nanoTime() - start;
            transactionDuration += txStartTime;
         }
         return txStartTime;
      }

      private long endTx(int iteration, long operationDuration) {
         transactionDuration += operationDuration;
         long start = System.nanoTime();
         //if we commit the transaction add the time needed for transaction commit as well
         long txEndTime = 0;
         if (completeTransaction(iteration, false)) {
            txEndTime = System.nanoTime() - start;
            txNotCompleted = false;
            transactionDuration += txEndTime;
         }
         return txEndTime;
      }

      private Object doRead(Object key, int iteration) {
         long txOverhead = 0;
         if (useTransactions) txOverhead = startTx(iteration);

         Object result = null;
         long start = System.nanoTime();
         try {
            result = cacheWrapper.get(bucketId, key);
         } catch (Exception e) {
            log.warn(e);
            nrFailures++;
         }
         long operationDuration = System.nanoTime() - start;

         if (useTransactions) txOverhead += endTx(iteration, operationDuration);
         readDuration += operationDuration;
         if (useTransactions) readDuration += txOverhead;
         reads++;
         return result;
      }
      
      private void doWrite(Object key, Object payload, int iteration) {
         long txOverhead = 0;
         if (useTransactions) txOverhead = startTx(iteration);

         long start = System.nanoTime();
         try {
            cacheWrapper.put(bucketId, key, payload);
         } catch (Exception e) {
            log.warn(e);
            nrFailures++;
         }
         long operationDuration = System.nanoTime() - start;

         if (useTransactions) txOverhead += endTx(iteration, operationDuration);
         writeDuration += operationDuration;
         if (useTransactions) writeDuration += txOverhead;
         writes++;
      }

      private void logProgress(int i, Object result) {
         if ((i + 1) % opsCountStatusLog == 0) {
            double elapsedNanos = System.nanoTime() - startNanos;
            double estimatedTotal = ((double) (numberOfRequests / numOfThreads) / (double) i) * elapsedNanos;
            double estimatedRemaining = estimatedTotal - elapsedNanos;
            if (log.isTraceEnabled()) {
               log.trace("i=" + i + ", elapsedTime=" + elapsedNanos);
            }
            log.info("Thread index '" + threadIndex + "' executed " + (i + 1) + " operations. Elapsed time: " +
                  Utils.getNanosDurationString((long) elapsedNanos) + ". Estimated remaining: " + Utils.getNanosDurationString((long) estimatedRemaining) +
                  ". Estimated total: " + Utils.getNanosDurationString((long) estimatedTotal));
            //this line was added just to make sure JIT doesn't skip call to cacheWrapper.get
            if (result == null) System.out.print("");
         }
      }

      public long totalDuration() {
         return readDuration + writeDuration;
      }

      public void initialiseKeys() {
         for (int keyIndex = 0; keyIndex < numberOfKeys; keyIndex++) {
            try {
               Object key;
               if (isLocalBenchmark()) {
                  key = getKeyGenerator().generateKey(threadIndex, keyIndex);
               } else {
                  key = getKeyGenerator().generateKey(nodeIndex, threadIndex, keyIndex);
               }
               pooledKeys.add(key);
               cacheWrapper.put(this.bucketId, key, generateRandomString(sizeOfValue));
            } catch (Throwable e) {
               log.warn("Error while initializing the session: ", e);
            }
         }
      }

      public Object getKey(int keyIndex) {
         return pooledKeys.get(keyIndex);
      }

      public long getTransactionsDuration() {
         return transactionDuration;
      }
   }

   private boolean startTransaction(int i) {
      if ((i  % transactionSize) == 0) {
         cacheWrapper.startTransaction();
         return true;
      }
      return false;
   }

   private boolean completeTransaction(int i, boolean force) {
      if ((((i + 1) % transactionSize) == 0) || force) {
         try {
            cacheWrapper.endTransaction(commitTransactions);
         } catch (Exception e) {
            log.error("Issues committing the transaction", e);
            throw new RuntimeException(e);
         }
         txCount.incrementAndGet();
         return true;
      }
      return false;
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

   private static String generateRandomString(int size) {
      // each char is 2 bytes
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < size / 2; i++) sb.append((char) (64 + r.nextInt(26)));
      return sb.toString();
   }

   public int getNodeIndex() {
      return nodeIndex;
   }

   public void setNodeIndex(int nodeIndex) {
      this.nodeIndex = nodeIndex;
   }

   public String getKeyGeneratorClass() {
      return keyGeneratorClass;
   }

   public void setKeyGeneratorClass(String keyGeneratorClass) {
      this.keyGeneratorClass = keyGeneratorClass;
      instantiateGenerator(keyGeneratorClass);
   }

   private void instantiateGenerator(String keyGeneratorClass) {
      keyGenerator = (KeyGenerator) Utils.instantiate(keyGeneratorClass);
   }

   public KeyGenerator getKeyGenerator() {
      if (keyGenerator == null) instantiateGenerator(keyGeneratorClass);
      return keyGenerator;
   }

   public int getTransactionSize() {
      return transactionSize;
   }

   public void setTransactionSize(int transactionSize) {
      this.transactionSize = transactionSize;
   }

   public boolean isUseTransactions() {
      return useTransactions;
   }

   public void setUseTransactions(boolean useTransactions) {
      this.useTransactions = useTransactions;
   }

   public boolean isCommitTransactions() {
      return commitTransactions;
   }

   public void setCommitTransactions(boolean commitTransactions) {
      this.commitTransactions = commitTransactions;
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
            ", cacheWrapper=" + cacheWrapper +
            ", nodeIndex=" + nodeIndex +
            ", useTransactions=" + useTransactions +
            ", transactionSize=" + transactionSize +
            ", commitTransactions=" + commitTransactions +
            "}";
   }
}

