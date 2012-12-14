package org.radargun.stressors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.utils.Utils;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * On multiple threads executes put and get operations against the CacheWrapper, and returns the result as an Map.
 *
 * @author Mircea.Markus@jboss.com
 */
public class StressTestStressor extends AbstractCacheWrapperStressor {

   private static Log log = LogFactory.getLog(StressTestStressor.class);
   private static final double NANOSECS_IN_SEC = 1000000000.0;

   private int opsCountStatusLog = 5000;

   /**
    * total number of operation to be made against cache wrapper: reads + writes
    */
   private int numRequests = 50000;

   /**
    * for each there will be created fixed number of keys. All the GETs and PUTs are performed on these keys only.
    */
   private int numEntries = 100;

   /**
    * Each key will be a byte[] of this size.
    */
   private int entrySize = 1000;

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
   private int numThreads = 10;

   /**
    * This node's index in the Radargun cluster.  -1 is used for local benchmarks.
    */
   private int nodeIndex = -1;

   private int transactionSize = 1;

   private boolean useTransactions = false;

   private boolean commitTransactions = true;

   private boolean sharedKeys;

   private AtomicInteger txCount = new AtomicInteger(0);

   private String keyGeneratorClass = StringKeyGenerator.class.getName();

   private KeyGenerator keyGenerator;


   private CacheWrapper cacheWrapper;
   private static Random r = new Random();
   private volatile long startNanos;
   private volatile CountDownLatch startPoint;
   private volatile CountDownLatch endPoint;
   private volatile CountDownLatch cleanUpPoint;
   private volatile StressorCompletion completion;
   private volatile boolean finished = false;
   private volatile boolean terminated = false;
   
   protected List<Stressor> stressors = new ArrayList<Stressor>(numThreads);

   protected void init(CacheWrapper wrapper) {
      this.cacheWrapper = wrapper;
      startNanos = System.nanoTime();
      log.info("Executing: " + this.toString());      
   }
   
   public Map<String, String> stress(CacheWrapper wrapper) {
      init(wrapper);
      StressorCompletion completion;
      if (durationMillis > 0) {
         completion = new TimeStressorCompletion(durationMillis);
      } else {
         completion = new OperationCountCompletion(new AtomicInteger(numRequests));
      }
      setStressorCompletion(completion);

      try {
         executeOperations();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      
      Map<String, String> results = processResults();
      finishOperations();
      return results;
   }

   public void destroy() throws Exception {
      cacheWrapper.empty();
      cacheWrapper = null;
   }

   protected boolean isTerminated() {
      return terminated;
   }

   private Map<String, String> processResults() {
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
      double requestPerSec = (reads + writes) / ((duration / numThreads) / NANOSECS_IN_SEC);
      results.put("REQ_PER_SEC", str(requestPerSec));
      if (reads > 0) {
         results.put("READS_PER_SEC", str(reads / ((readsDurations / numThreads) / NANOSECS_IN_SEC)));
      } else {
         results.put("READS_PER_SEC", str(0));
      }
      if (writes > 0) {
         results.put("WRITES_PER_SEC", str(writes / ((writesDurations / numThreads) / NANOSECS_IN_SEC)));
      } else {
         results.put("WRITES_PER_SEC", str(0));
      }
      results.put("READ_COUNT", str(reads));
      results.put("WRITE_COUNT", str(writes));
      results.put("FAILURES", str(failures));
      if (useTransactions) {
         double txPerSec = getTxCount() / ((transactionDuration / numThreads) / NANOSECS_IN_SEC);
         results.put("TX_PER_SEC", str(txPerSec));
      }
      log.info("Finished generating report. Nr of failed operations on this node is: " + failures +
                     ". Test duration is: " + Utils.getNanosDurationString(System.nanoTime() - startNanos));
      return results;
   }

   protected void executeOperations() throws Exception {      
      startPoint = new CountDownLatch(1);
      if (cleanUpPoint != null) {
         cleanUpPoint.countDown();
      }
      cleanUpPoint = new CountDownLatch(1);
      endPoint = new CountDownLatch(numThreads);
      for (int threadIndex = stressors.size(); threadIndex < numThreads; threadIndex++) {
         Stressor stressor = new Stressor(threadIndex);
         stressor.initialiseKeys();
         stressors.add(stressor);
         stressor.start();
      }
      log.info("Cache wrapper info is: " + cacheWrapper.getInfo());
      startPoint.countDown();
      log.info("Started " + stressors.size() + " stressor threads.");
            
      endPoint.await();
   }
   
   protected void finishOperations() {
      finished = true;
      cleanUpPoint.countDown();
      for (Stressor s : stressors) {
         try {
            s.join();
         } catch (InterruptedException e) {
            throw new RuntimeException(e);
         }
      }
      stressors.clear();
   }
   
   protected void setStressorCompletion(StressorCompletion completion) {
      this.completion = completion;
   }

   private boolean isLocalBenchmark() {
      return nodeIndex == -1;
   }

   public void setSharedKeys(boolean sharedAttributes) {
      this.sharedKeys = sharedAttributes;
   }

   public boolean isSharedKeys() {
      return sharedKeys;
   }

   protected class Stressor extends Thread {

      private ArrayList<Object> pooledKeys = new ArrayList<Object>(numEntries);

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
            do {
               try {
                  runInternal();
               } catch (Exception e) {
                  terminated = true;
                  throw e;
               } finally {
                  endPoint.countDown();
               }
               cleanUpPoint.await();
               clean();
            } while (!finished);
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
            randomKeyInt = r.nextInt(numEntries - 1);
            Object key = getKey(randomKeyInt);
            Object result = null;

            if (randomAction < readPercentage) {
               result = doRead(key, i);
            } else {
               doWrite(key, generateRandomBytes(entrySize), i);
            }

            i++;
            completion.logProgress(i, result, threadIndex);
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

      public long totalDuration() {
         return readDuration + writeDuration;
      }

      public void initialiseKeys() {
         for (int keyIndex = 0; keyIndex < numEntries; keyIndex++) {
            try {
               Object key;
               if (isSharedKeys()) {
                  key = getKeyGenerator().generateKey(keyIndex);
               } else if (isLocalBenchmark()) {
                  key = getKeyGenerator().generateKey(threadIndex, keyIndex);
               } else {
                  key = getKeyGenerator().generateKey(nodeIndex, threadIndex, keyIndex);
               }
               pooledKeys.add(key);
               cacheWrapper.put(this.bucketId, key, generateRandomBytes(entrySize));
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

      public int getNrFailures() {
         return nrFailures;
      }

      public long getReadDuration() {
         return readDuration;
      }

      public long getWriteDuration() {
         return writeDuration;
      }

      public long getReads() {
         return reads;
      }

      public long getWrites() {
         return writes;
      }
      
      private void clean() {
         nrFailures = 0;
         readDuration = 0;
         writeDuration = 0;
         transactionDuration = 0;
         reads = 0;
         writes = 0;
      }
   }

   private boolean startTransaction(int i) {
      if ((i % transactionSize) == 0) {
         try {
            cacheWrapper.startTransaction();
         } catch (Exception e) {
            log.error("Cannot start transaction");
         }
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
         }
         txCount.incrementAndGet();
         return true;
      }
      return false;
   }

   protected static String str(Object o) {
      return String.valueOf(o);
   }

   protected int getTxCount() {
      return txCount.get();
   }
   
   public int getNumRequests() {
      return numRequests;
   }
      
   public int getNumThreads() {
      return numThreads;
   }
   
   public void setNumRequests(int numberOfRequests) {
      this.numRequests = numberOfRequests;
   }

   public void setNumEntries(int numberOfKeys) {
      this.numEntries = numberOfKeys;
   }

   public void setEntrySize(int sizeOfValue) {
      this.entrySize = sizeOfValue;
   }

   public void setNumThreads(int numOfThreads) {
      this.numThreads = numOfThreads;
   }

   public void setWritePercentage(int writePercentage) {
      this.writePercentage = writePercentage;
   }

   public void setOpsCountStatusLog(int opsCountStatusLog) {
      this.opsCountStatusLog = opsCountStatusLog;
   }

   private static byte[] generateRandomBytes(int size) {
      byte[] array = new byte[size];
      r.nextBytes(array);
      return array;
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
         double elapsedNanos = System.nanoTime() - startNanos;
         double estimatedTotal = ((double) (numRequests / numThreads) / (double) i) * elapsedNanos;
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
      
      private final AtomicInteger requestsLeft;

      OperationCountCompletion(AtomicInteger requestsLeft) {
         this.requestsLeft = requestsLeft;
      }

      @Override
      public boolean moreToRun() {
         return requestsLeft.getAndDecrement() > -1;
      }
   }
   
   class TimeStressorCompletion extends StressorCompletion {
      
      private volatile long startTime = -1;
      
      private final long durationMillis;

      private volatile long lastPrint = -1;
      
      TimeStressorCompletion(long durationMillis) {
         this.durationMillis = durationMillis;         
      }

      @Override
      boolean moreToRun() {
         // Synchronize the start until someone is ready
         // we don't care about the race condition here
         if (startTime == -1) {
            startTime = nowMillis();
         }
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
      return "StressTestStressor{" +
            "opsCountStatusLog=" + opsCountStatusLog +
            ", numRequests=" + numRequests +
            ", numEntries=" + numEntries +
            ", entrySize=" + entrySize +
            ", writePercentage=" + writePercentage +
            ", numThreads=" + numThreads +
            ", cacheWrapper=" + cacheWrapper +
            ", nodeIndex=" + nodeIndex +
            ", useTransactions=" + useTransactions +
            ", transactionSize=" + transactionSize +
            ", commitTransactions=" + commitTransactions +
            ", durationMillis=" + durationMillis +
            "}";
   }
}

