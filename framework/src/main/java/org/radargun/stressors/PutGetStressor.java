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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * On multiple threads executes put and get operations against the CacheWrapper, and returns the result as an Map.
 *
 * @author Mircea.Markus@jboss.com
 */
public class PutGetStressor extends AbstractCacheWrapperStressor {

   private static final Log log = LogFactory.getLog(PutGetStressor.class);

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
    * Negative values means duration is not enabled.
    */
   private long durationMillis = -1;


   /**
    * the number of threads that will work on this cache wrapper.
    */
   private int numOfThreads = 10;
   
   private boolean cacheInitialKeys = true;

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
   private static final Random r = new Random();
   private volatile long startNanos;
   private volatile CountDownLatch startPoint;
   private volatile StressorCompletion completion;


   public Map<String, String> stress(CacheWrapper wrapper) {
      this.cacheWrapper = wrapper;
      log.info("Executing: " + this.toString());
      startNanos = System.nanoTime();
      if (durationMillis > 0) {
         completion = new TimeStressorCompletion(durationMillis);
      } else {
         completion = new OperationCountCompletion(new AtomicInteger(numberOfRequests));
      }

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

   protected Map<String, String> processResults(List<Stressor> stressors) {
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
      results.put("DURATION", str(transactionDuration));
      double requestPerSec = (reads + writes) / ((duration / numOfThreads) / 1000000000.0);
      results.put("REQ_PER_SEC", str(requestPerSec));
      results.put("READS_PER_SEC", str(reads / ((readsDurations / numOfThreads) / 1000000000.0)));
      results.put("WRITES_PER_SEC", str(writes / ((writesDurations / numOfThreads) / 1000000000.0)));
      results.put("READ_COUNT", str(reads));
      results.put("WRITE_COUNT", str(writes));
      results.put("FAILURES", str(failures));
      if (useTransactions) {
         double txPerSec = txCount.get() / ((transactionDuration / numOfThreads) / 1000000000.0);
         results.put("TX_PER_SEC", str(txPerSec));
      }
      log.info("Finished generating report. Nr of failed operations on this node is: " + failures +
                     ". Test duration is: " + Utils.getNanosDurationString(System.nanoTime() - startNanos));
      return results;
   }

   private List<Stressor> executeOperations() throws Exception {
      List<Stressor> stressors = new ArrayList<Stressor>(numOfThreads);
      startPoint = new CountDownLatch(1);
      for (int threadIndex = 0; threadIndex < numOfThreads; threadIndex++) {
         Stressor stressor = new Stressor(threadIndex);
         stressor.initialiseKeys();
         stressors.add(stressor);
         stressor.start();
      }
      log.info("Cache wrapper info is: " + cacheWrapper.getInfo());
      startPoint.countDown();
      completion.start();
      log.info("Started " + stressors.size() + " stressor threads.");
      for (Stressor stressor : stressors) {
         stressor.join();
      }
      return stressors;
   }

   private boolean isLocalBenchmark() {
      return nodeIndex == -1;
   }

   protected class Stressor extends Thread {

      private ArrayList<Object> pooledKeys = new ArrayList<Object>(numberOfKeys);

      private int threadIndex;
      private int nrFailures;
      private long readDuration = 0;
      private long writeDuration = 0;
      private long transactionDuration = 0;
      private long reads;
      private long writes;
      private final String bucketId;
      boolean txNotCompleted = false;

      public Stressor(int threadIndex) {
         super("Stressor-" + threadIndex);
         this.threadIndex = threadIndex;
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
         int readPercentage = 100 - writePercentage;
         Random r = new Random();
         int randomAction;
         int randomKeyInt;
         try {
            startPoint.await();
            log.trace("Starting thread: " + getName());
         } catch (InterruptedException e) {
            log.warn(e);
         }

         int i = 0;
         while (completion.moreToRun()) {
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
            completion.logProgress(i, result, threadIndex);
         }
         
         log.info("Open Transactions: " + txNotCompleted);

         if (txNotCompleted) {
            long txEndDuration = endTransaction();
            transactionDuration += txEndDuration;
         }
      }

      private Object doRead(Object key, int iteration) {
         Object result = null;
         long startTxDuration = 0;
         if (useTransactions && shouldStartTransaction(iteration)) {
            startTxDuration += startTransaction();
            txNotCompleted = true;
         }
         long start = System.nanoTime();
         try {
            result = cacheWrapper.get(bucketId, key);
         } catch (Exception e) {
            log.warn(e);
            nrFailures++;
         }
         long operationDuration = System.nanoTime() - start;
         long endTxDuration = 0;
         if (useTransactions && shouldEndTransaction(iteration)) {
            endTxDuration += endTransaction();
            txNotCompleted = false;
         }
         readDuration += operationDuration;
         transactionDuration += startTxDuration;
         transactionDuration += operationDuration;
         transactionDuration += endTxDuration;
         reads++;
         return result;
      }

      private void doWrite(Object key, Object payload, int iteration) {
         long startTxDuration = 0;
         if (useTransactions && shouldStartTransaction(iteration))
            startTxDuration += startTransaction();
            txNotCompleted = true;
         long start = System.nanoTime();
         try {
            cacheWrapper.put(bucketId, key, payload);
         } catch (Exception e) {
            log.warn(e);
            nrFailures++;
         }
         long operationDuration = System.nanoTime() - start;
         long endTxDuration = 0;
         if (useTransactions && shouldEndTransaction(iteration)) {
            endTxDuration += endTransaction();
            txNotCompleted = false;
         }
         writeDuration += operationDuration;
         transactionDuration += startTxDuration;
         transactionDuration += operationDuration;
         transactionDuration += endTxDuration;
         writes++;
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
               if (cacheInitialKeys) {
                  if (useTransactions) cacheWrapper.startTransaction();
                  cacheWrapper.put(this.bucketId, key, generateRandomString(sizeOfValue));
                  if (useTransactions) cacheWrapper.endTransaction(true);
               }
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
   
   private boolean shouldStartTransaction(int i) {
      if ((i % transactionSize) == 0) {
         return true;
      }
      return false;
   }

   private long startTransaction() {
      long start = System.nanoTime();
      cacheWrapper.startTransaction();
      long txStartTime = System.nanoTime() - start;
      return txStartTime;
   }
   
	private boolean shouldEndTransaction(int i) {
	   if (((i + 1) % transactionSize) == 0) {
			return true;
		}
		return false;
	}

   private long endTransaction() {
      long start = System.nanoTime();
      try {
         cacheWrapper.endTransaction(commitTransactions);
      } catch (Exception e) {
         log.error("Issues committing the transaction", e);
         throw new RuntimeException(e);
      }
      long txEndTime = System.nanoTime() - start;
      txCount.incrementAndGet();
      return txEndTime;
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

   public void setNumberOfKeys(int numberOfKeys) {
      this.numberOfKeys = numberOfKeys;
   }

   public void setSizeOfValue(int sizeOfValue) {
      this.sizeOfValue = sizeOfValue;
   }
   
   public void setCacheInitialKeys(boolean cacheInitialKeys) {
      this.cacheInitialKeys = cacheInitialKeys;
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

   public long getDurationMillis() {
      return durationMillis;
   }

   public void setDurationMillis(long durationMillis) {
      this.durationMillis = durationMillis;
   }

   public void setDuration(String duration) {
      this.durationMillis = Utils.string2Millis(duration);
   }


   abstract class StressorCompletion {
	   
	  abstract void start();
	  
	  abstract long getStartTime();
 
      abstract boolean moreToRun();

      public void logProgress(int i, Object result, int threadIndex) {
         if (shoulLogBasedOnOpCount(i)) {
            avoidJit(result);
            logRemainingTime(i, threadIndex);
         }
      }

      protected boolean shoulLogBasedOnOpCount(int i) {
         return (i + 1) % opsCountStatusLog == 0;
      }

      protected void logRemainingTime(int i, int threadIndex) {
         double elapsedNanos = System.nanoTime() - getStartTime();
         double estimatedTotal = ((double) (numberOfRequests / numOfThreads) / (double) i) * elapsedNanos;
         double estimatedRemaining = estimatedTotal - elapsedNanos;
         if (log.isTraceEnabled()) {
            log.trace("i=" + i + ", elapsedTime=" + elapsedNanos);
         }
         log.info("Thread index '" + threadIndex + "' executed " + (i + 1) + " operations. Elapsed time: " +
                        Utils.getNanosDurationString((long) elapsedNanos) + ". Estimated remaining: " + Utils.getNanosDurationString((long) estimatedRemaining) +
                        ". Estimated total: " + Utils.getNanosDurationString((long) estimatedTotal));
      }

      protected void avoidJit(Object result) {
         //this line was added just to make sure JIT doesn't skip call to cacheWrapper.get
         if (result != null && System.identityHashCode(result) == result.hashCode()) System.out.print("");
      }
   }

   class OperationCountCompletion extends StressorCompletion {
	   
	  private volatile long startTimeNanos;
      
      private final AtomicInteger requestsLeft;

      OperationCountCompletion(AtomicInteger requestsLeft) {
         this.requestsLeft = requestsLeft;
      }
      
      @Override
      public void start() {
    	  startTimeNanos = System.nanoTime();
      }

      @Override
      public long getStartTime() {
    	  return startTimeNanos;
      }
      
      @Override
      public boolean moreToRun() {
         return requestsLeft.getAndDecrement() > -1;
      }
   }
   
   class TimeStressorCompletion extends StressorCompletion {
      
      private volatile long startTime;
      private volatile long startTimeNanos;
      
      private final long durationMillis;

      private volatile long lastPrint = -1;
      
      TimeStressorCompletion(long durationMillis) {
         this.durationMillis = durationMillis;
      }
      
      @Override
      public void start() {
         startTime = nowMillis();
         startTimeNanos = System.nanoTime();
      }
      
      @Override
      public long getStartTime() {
    	 return startTimeNanos;
      }

      @Override
      boolean moreToRun() {
         return nowMillis() <= startTime + durationMillis;
      }

      public void logProgress(int i, Object result, int threadIndex) {
         long nowMillis = nowMillis();

         //make sure this info is not printed more frequently than 20 secs
         int logFrequency = 20;
         if (lastPrint > 0 && (getSecondsSinceLastPrint(nowMillis) < logFrequency)) return; {
            synchronized (this) {
               if (getSecondsSinceLastPrint(nowMillis) < logFrequency) return;
               avoidJit(result);

               lastPrint = nowMillis;
               long elapsedMillis = nowMillis - startTime;

               //make sure negative durations are not printed
               long remaining = Math.max(0, (startTime + durationMillis) - nowMillis);

               log.info("Number of ops executed so far: " + i + ". Elapsed time: " + Utils.getMillisDurationString(elapsedMillis) + ". Remaining: " + Utils.getMillisDurationString(remaining) +
                              ". Total: " + Utils.getMillisDurationString(durationMillis));
            }
         }
      }

      private long getSecondsSinceLastPrint(long nowMillis) {
         return TimeUnit.MILLISECONDS.toSeconds(nowMillis - lastPrint);
      }

      private long nowMillis() {
         return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
      }
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
            ", durationMillis=" + durationMillis +
            "}";
   }
}

