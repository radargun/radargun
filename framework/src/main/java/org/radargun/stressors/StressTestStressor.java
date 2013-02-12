package org.radargun.stressors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.utils.Utils;

/**
 * On multiple threads executes put and get operations against the CacheWrapper, and returns the result as an Map.
 *
 * @author Mircea.Markus@jboss.com
 */
public class StressTestStressor extends AbstractCacheWrapperStressor {

   private static final Log log = LogFactory.getLog(StressTestStressor.class);

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

   private int numNodes = 1;

   /**
    * This node's index in the Radargun cluster.  -1 is used for local benchmarks.
    */
   private int nodeIndex = -1;

   private int transactionSize = 1;

   private boolean useTransactions = false;

   private boolean commitTransactions = true;

   private boolean sharedKeys = false;

   private boolean fixedKeys = true;

   private AtomicInteger txCount = new AtomicInteger(0);

   private String keyGeneratorClass = StringKeyGenerator.class.getName();

   private KeyGenerator keyGenerator;

   private CacheWrapper cacheWrapper;
   private ArrayList<Object> sharedKeysPool = null;
   private static final Random r = new Random();
   private volatile long startNanos;
   private volatile CountDownLatch startPoint;
   private volatile CountDownLatch endPoint;
   private volatile CountDownLatch cleanUpPoint;
   private volatile CountDownLatch keyInitPoint;
   private volatile StressorCompletion completion;
   private volatile boolean finished = false;
   private volatile boolean terminated = false;
   
   protected List<Stressor> stressors = new ArrayList<Stressor>(numThreads);

   protected void init(CacheWrapper wrapper) {
      this.cacheWrapper = wrapper;
      startNanos = System.nanoTime();
      log.info("Executing: " + this.toString());
   }
   
   public Map<String, Object> stress(CacheWrapper wrapper) {
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
      
      Map<String, Object> results = processResults();
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

   private Map<String, Object> processResults() {
      Statistics stats = new Statistics();

      for (Stressor stressor : stressors) {
         stats.merge(stressor.stats);
      }

      Map<String, Object> results = new LinkedHashMap<String, Object>();
      results.put("DURATION", stats.getResponseTimeSum() + stats.getTxOverheadSum());
      results.put("REQ_PER_SEC", numThreads * stats.getOperationsPerSecond());
      results.put("READS_PER_SEC", numThreads * stats.getReadsPerSecond(true));
      results.put("READS_PER_SEC_NET", numThreads * stats.getReadsPerSecond(false));
      results.put("WRITES_PER_SEC", numThreads * stats.getWritesPerSecond(true));
      results.put("WRITES_PER_SEC_NET", numThreads * stats.getWritesPerSecond(false));
      if (stats.getNumRemoves() > 0) {
         results.put("REMOVES_PER_SEC", numThreads * stats.getRemovesPerSecond(true));
         results.put("REMOVES_PER_SEC_NET", numThreads * stats.getRemovesPerSecond(false));
      }
      results.put("READ_COUNT", stats.getNumReads());
      results.put("WRITE_COUNT", stats.getNumWrites());
      if (stats.getNumRemoves() > 0) {
         results.put("REMOVE_COUNT", stats.getNumRemoves());
      }
      results.put("FAILURES", stats.getNumErrors());
      if (useTransactions) {
         results.put("TX_COUNT", stats.getNumTransactions());
         results.put("TX_PER_SEC", numThreads * stats.getTransactionsPerSecond());
      }
      log.info("Finished generating report. Nr of failed operations on this node is: " + stats.getNumErrors() +
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
      keyInitPoint = new CountDownLatch(numThreads - stressors.size());
      for (int threadIndex = stressors.size(); threadIndex < numThreads; threadIndex++) {
         Stressor stressor = new Stressor(threadIndex, getLogic());
         stressors.add(stressor);
         stressor.start();
      }
      log.info("Cache wrapper info is: " + cacheWrapper.getInfo());
      keyInitPoint.await();
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

   public void setSharedKeys(boolean sharedKeys) {
      this.sharedKeys = sharedKeys;
   }

   public void setFixedKeys(boolean fixedKeys) {
      this.fixedKeys = fixedKeys;
   }

   public OperationLogic getLogic() {
      if (sharedKeys && !fixedKeys) {
         throw new IllegalArgumentException("Cannot use both shared and non-fixed keys - not implemented");
      } else if (!fixedKeys) {
         return new ChangingSetOperationLogic();
      } else if (sharedKeys) {
         if (sharedKeysPool == null) {
            sharedKeysPool = new ArrayList<Object>();
         }
         return new FixedSetSharedOperationLogic(sharedKeysPool);
      } else {
         return new FixedSetPerThreadOperationLogic();
      }
   }

   protected interface OperationLogic {
      void init(String bucketId, int threadIndex);
      Object run(Stressor stressor, int iteration);
   }

   protected abstract class FixedSetOperationLogic implements OperationLogic {
      private Random r = new Random();

      @Override
      public Object run(Stressor stressor, int iteration) {
         int randomAction = r.nextInt(100);
         int randomKeyInt = r.nextInt(numEntries - 1);
         Object key = getKey(randomKeyInt);

         if (randomAction < writePercentage) {
            return stressor.makeRequest(key, iteration, Operation.PUT, generateRandomBytes(entrySize));
         } else {
            return stressor.makeRequest(key, iteration, Operation.GET, null);
         }
      }

      public abstract Object getKey(int keyId);
   }

   protected class FixedSetPerThreadOperationLogic extends FixedSetOperationLogic {
      private ArrayList<Object> pooledKeys = new ArrayList<Object>(numEntries);

      @Override
      public void init(String bucketId, int threadIndex) {
         KeyGenerator keyGenerator = getKeyGenerator();
         for (int keyIndex = 0; keyIndex < numEntries; keyIndex++) {
            Object key = null;
            try {
               if (isLocalBenchmark()) {
                  key = keyGenerator.generateKey(threadIndex, keyIndex);
               } else {
                  key = keyGenerator.generateKey(nodeIndex, threadIndex, keyIndex);
               }
               cacheWrapper.put(bucketId, key, generateRandomBytes(entrySize));
               pooledKeys.add(key);
            } catch (Throwable e) {
               log.warn("Failed to insert key " + key, e);
            }
         }
      }

      public Object getKey(int keyId) {
         return pooledKeys.get(keyId);
      }
   }

   protected class FixedSetSharedOperationLogic extends FixedSetOperationLogic {

      private ArrayList<Object> sharedKeys;

      public FixedSetSharedOperationLogic(ArrayList<Object> sharedKeys) {
         this.sharedKeys = sharedKeys;
      }

      @Override
      public Object getKey(int keyId) {
         return sharedKeys.get(keyId);
      }

      @Override
      public void init(String bucketId, int threadIndex) {
         KeyGenerator keyGenerator = getKeyGenerator();
         // no point in doing this in parallel, too much overhead in synchronization
         if (threadIndex == 0) {
            sharedKeys.clear();
            for (int keyIndex = 0; keyIndex < numEntries; ++keyIndex) {
               sharedKeys.add(keyGenerator.generateKey(keyIndex));
            }
         }
         int totalThreads = numThreads * numNodes;
         for (int keyIndex = threadIndex + nodeIndex * numThreads; keyIndex < numEntries; keyIndex += totalThreads) {
            try {
               cacheWrapper.put(null, keyGenerator.generateKey(keyIndex), generateRandomBytes(entrySize));
            } catch (Exception e) {
               log.error("Failed to insert shared key " + keyIndex, e);
            }
         }
      }
   }

   private static class KeyWithRemovalTime implements Comparable<KeyWithRemovalTime> {
      public Object key;
      public long removeTimestamp;

      public KeyWithRemovalTime(Object key, long removeTimestamp) {
         this.key = key;
         this.removeTimestamp = removeTimestamp;
      }

      @Override
      public int compareTo(KeyWithRemovalTime o) {
         return o.removeTimestamp == removeTimestamp ? 0 : removeTimestamp < o.removeTimestamp ? -1 : 1;
      }
   }

   protected class ChangingSetOperationLogic implements OperationLogic {
      private TreeSet<KeyWithRemovalTime> scheduledKeys = new TreeSet<KeyWithRemovalTime>();
      private Random r = new Random();
      private int threadIndex;
      private long nextKey = 0;

      @Override
      public void init(String bucketId, int threadIndex) {
         this.threadIndex = threadIndex;
      }

      @Override
      public Object run(Stressor stressor, int iteration) {
         long timestamp = System.currentTimeMillis();
         if (!scheduledKeys.isEmpty() && scheduledKeys.first().removeTimestamp <= timestamp) {
            return stressor.makeRequest(scheduledKeys.pollFirst().key, iteration, Operation.REMOVE, null);
         } else if (r.nextInt(100) >= writePercentage && scheduledKeys.size() > 0) {
            // we cannot get random access to PriorityQueue and there is no SortedList or another appropriate structure
            return stressor.makeRequest(getRandomKey(timestamp), iteration, Operation.GET, null);
         } else {
            Object key;
            if (scheduledKeys.size() < numEntries) {
               key = getKeyGenerator().generateKey(nodeIndex, threadIndex, nextKey++);
               KeyWithRemovalTime pair = new KeyWithRemovalTime(key, getRandomTimestamp(timestamp));
               scheduledKeys.add(pair);
            } else {
               key = getRandomKey(timestamp);
            }
            return stressor.makeRequest(key, iteration, Operation.PUT, generateRandomBytes(entrySize));
         }
      }

      private long getRandomTimestamp(long current) {
         // ~sqrt probability for 1 - maxRoot^2
         final int maxRoot = 100;
         int rand = r.nextInt(maxRoot);
         return current + rand * rand + r.nextInt(2*maxRoot - 2) + 1;
      }

      private Object getRandomKey(long timestamp) {
         KeyWithRemovalTime pair = scheduledKeys.floor(new KeyWithRemovalTime(null, getRandomTimestamp(timestamp)));
         return pair == null ? scheduledKeys.first().key : pair.key;
      }
   }

   protected class Stressor extends Thread {
      private int threadIndex;
      private final String bucketId;
      boolean txNotCompleted = false;
      long transactionDuration = 0;
      public Statistics stats = new Statistics();
      private OperationLogic logic;

      public Stressor(int threadIndex, OperationLogic logic) {
         super("Stressor-" + threadIndex);         
         this.threadIndex = threadIndex;
         this.logic = logic;
         this.bucketId = isLocalBenchmark() ? String.valueOf(threadIndex) : nodeIndex + "_" + threadIndex;
      }

      @Override
      public void run() {
         try {
            logic.init(bucketId, threadIndex);
            keyInitPoint.countDown();
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
         try {
            startPoint.await();
            log.trace("Starting thread: " + getName());
         } catch (InterruptedException e) {
            log.warn(e);
         }

         int i = 0;
         while (completion.moreToRun()) {
            Object result = logic.run(this, i);
            i++;
            completion.logProgress(i, result, threadIndex);
         }

         if (txNotCompleted) {
            try {
               long endTxTime = endTransaction();
               stats.registerRequest(transactionDuration + endTxTime, 0, Operation.TRANSACTION, false);
            } catch (TransactionException e) {
               stats.registerError(transactionDuration + e.getOperationDuration(), 0, Operation.TRANSACTION);
            }
            transactionDuration = 0;
         }
      }

      public Object makeRequest(Object key, int iteration, Operation operation, Object payload) {
         long startTxTime = 0;
         if (useTransactions && shouldStartTransaction(iteration)) {
            try {
               startTxTime = startTransaction();
               transactionDuration = startTxTime;
               txNotCompleted = true;
            } catch (TransactionException e) {
               stats.registerError(e.getOperationDuration(), 0, Operation.TRANSACTION);
               return null;
            }
         }

         Object result = null;
         boolean successfull;
         long start = System.nanoTime();
         long operationDuration;
         try {
            switch (operation) {
               case GET:
                  result = cacheWrapper.get(bucketId, key);
                  break;
               case PUT:
                  cacheWrapper.put(bucketId, key, payload);
                  break;
               case REMOVE:
                  result = cacheWrapper.remove(bucketId, key);
                  break;
               default:
                  throw new IllegalArgumentException();
            }
            operationDuration = System.nanoTime() - start;
            successfull = true;
         } catch (Exception e) {
            operationDuration = System.nanoTime() - start;
            log.warn(e);
            successfull = false;
         }
         transactionDuration += operationDuration;

         long endTxTime = 0;
         if (useTransactions && shouldEndTransaction(iteration)) {
            try {
               endTxTime = endTransaction();
               stats.registerRequest(transactionDuration + endTxTime, 0, Operation.TRANSACTION, false);
               txNotCompleted = false;
            } catch (TransactionException e) {
               endTxTime = e.getOperationDuration();
               stats.registerError(transactionDuration + endTxTime, 0, Operation.TRANSACTION);
            }
         }
         if (successfull) {
            stats.registerRequest(operationDuration, startTxTime + endTxTime, operation, result == null);
         } else {
            stats.registerError(operationDuration, startTxTime + endTxTime, operation);
         }
         return result;
      }

      private void clean() {
         stats = new Statistics();
      }

      private class TransactionException extends Exception {
         private final long operationDuration;

         public TransactionException(long duration, Exception cause) {
            super(cause);
            this.operationDuration = duration;
         }

         public long getOperationDuration() {
            return operationDuration;
         }
      }

      private long startTransaction() throws TransactionException {
         long start = System.nanoTime();
         try {
            cacheWrapper.startTransaction();
         } catch (Exception e) {
            long time = System.nanoTime() - start;
            log.error("Failed to start transaction", e);
            throw new TransactionException(time, e);
         }
         return System.nanoTime() - start;
      }

      private long endTransaction() throws TransactionException {
         long start = System.nanoTime();
         try {
            cacheWrapper.endTransaction(commitTransactions);
         } catch (Exception e) {
            long time = System.nanoTime() - start;
            log.error("Failed to end transaction", e);
            throw new TransactionException(time, e);
         }
         return System.nanoTime() - start;
      }
   }

   private boolean shouldStartTransaction(int i) {
      return (i % transactionSize) == 0;
   }

   private boolean shouldEndTransaction(int i) {
      return ((i + 1) % transactionSize) == 0;
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

   public void setNodeIndex(int nodeIndex, int numNodes) {
      this.nodeIndex = nodeIndex;
      this.numNodes = numNodes;
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

