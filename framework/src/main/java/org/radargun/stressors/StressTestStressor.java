package org.radargun.stressors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.config.Property;
import org.radargun.config.Stressor;
import org.radargun.config.TimeConverter;
import org.radargun.features.AtomicOperationsCapable;
import org.radargun.features.BulkOperationsCapable;
import org.radargun.features.Queryable;
import org.radargun.utils.Fuzzy;
import org.radargun.utils.Utils;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * On multiple threads executes put and get operations against the CacheWrapper, and returns the result as an Map.
 *
 * @author Mircea.Markus@jboss.com
 */
@Stressor(doc = "Executes put and get operations agains the cache wrapper.")
public class StressTestStressor extends AbstractCacheWrapperStressor {

   private static final Log log = LogFactory.getLog(StressTestStressor.class);

   @Property(doc = "After how many operations should be log written. Default is 5000.")
   private int opsCountStatusLog = 5000;

   @Property(doc = "Total number of operation to be made against cache wrapper: reads + writes. Default is 50000.")
   private int numRequests = 50000;

   @Property(doc = "Number of keys on which all the GETs and PUTs are performed. Default is 100.")
   private int numEntries = 100;

   @Property(doc = "Applicable only with fixedKeys=false, makes sense for entrySize with multiple values. " +
         "Replaces numEntries; requested number of bytes in values set by the stressor. By default not set.")
   private long numBytes = 0;

   @Property(doc = "Size of the entry in bytes. Default is 1000.", converter = Fuzzy.IntegerConverter.class)
   private Fuzzy<Integer> entrySize = Fuzzy.always(1000);

   @Property(doc = "The frequency of writes (percentage). Default is 20%")
   private int writePercentage = 20;

   @Property(doc = "The frequency of removes (percentage). Default is 0%")
   private int removePercentage = 0;

   @Property(doc = "Used only when useAtomics=true: The frequency of conditional removes that should fail (percentage). Default is 10%")
   private int removeInvalidPercentage = 10;

   @Property(doc = "Used only when useAtomics=true: the frequency of replaces that should fail (percentage). Default is 40%")
   private int replaceInvalidPercentage = 40;

   @Property(doc = "Duration of the test. By default the duration depends on number of requests.", converter = TimeConverter.class)
   private long durationMillis = -1;

   @Property(doc = "The number of threads that will work on this cache wrapper. Default is 10.")
   private int numThreads = 10;

   @Property(doc = "Number of requests in one transaction. By default transactions are off.")
   private int transactionSize = 1;

   @Property(doc = "Number of keys inserted/retrieved within one operation. Applicable only when the cache wrapper" +
         "supports bulk operations. Default is 1 (no bulk operations).")
   private int bulkSize = 1;

   @Property(doc = "When executing bulk operations, prefer version with multiple async operations over native implementation. Default is false.")
   private boolean preferAsyncOperations = false;

   @Property(doc = "Specifies if the requests should be explicitely wrapped in transactions. By default" +
         "the cachewrapper is queried whether it does support the transactions, if it does," +
         "transactions are used, otherwise these are not.")
   private Boolean useTransactions = false;

   @Property(doc = "If this is set to true, transactions are committed in the end. Otherwise these are rolled back. Default is true.")
   private boolean commitTransactions = true;

   @Property(doc = "By default each client thread operates on his private set of keys. Setting this to true " +
         "introduces contention between the threads, the numThreads property says total amount of entries that are " +
         "used by all threads. Default is false.")
   protected boolean sharedKeys = false;

   @Property(doc = "This option is valid only for sharedKeys=true. It forces local loading of all keys (not only numEntries/numNodes). Default is false.")
   protected boolean loadAllKeys = false;

   @Property(doc = "The keys can be fixed for the whole test run period or we the set can change over time. Default is true = fixed.")
   protected boolean fixedKeys = true;

   @Property(doc = "")
   protected long entryLifespan = 1000000;

   @Property(doc = "If true, putIfAbsent and replace operations are used. Default is false.")
   protected boolean useAtomics = false;

   @Property(doc = "Keep all keys in a pool - do not generate the keys for each request anew. Default is true.")
   protected boolean poolKeys = true;

   @Property(doc = "Full class name of the key generator. Default is org.radargun.stressors.StringKeyGenerator.")
   private String keyGeneratorClass = StringKeyGenerator.class.getName();

   @Property(doc = "Used to initialize the key generator. Null by default.")
   private String keyGeneratorParam = null;

   @Property(doc = "If true, each thread will write into its own bucket in form 'bucket_/threadId/'. Default is false.")
   private boolean useBuckets = false;

   /**
    * Number of slaves that participate in this test
    */
   private int numNodes = 1;

   /**
    * This node's index in the Radargun cluster.  -1 is used for local benchmarks.
    */
   private int nodeIndex = -1;

   private AtomicInteger txCount = new AtomicInteger(0);

   protected KeyGenerator keyGenerator;

   protected CacheWrapper cacheWrapper;
   protected AtomicOperationsCapable atomicCacheWrapper;
   protected BulkOperationsCapable bulkCacheWrapper;
   private ArrayList<Object> sharedKeysPool = new ArrayList<Object>();
   private static final Random r = new Random();
   private volatile long startNanos;
   private PhaseSynchronizer synchronizer = new PhaseSynchronizer();
   private volatile StressorCompletion completion;
   private volatile boolean finished = false;
   private volatile boolean terminated = false;
   private AtomicLong keysLoaded = new AtomicLong(0);
   
   protected List<Stressor> stressors = new ArrayList<Stressor>(numThreads);
   private Statistics statisticsPrototype = new SimpleStatistics();

   protected void init(CacheWrapper wrapper) {
      this.cacheWrapper = wrapper;
      if (wrapper instanceof AtomicOperationsCapable) {
         atomicCacheWrapper = (AtomicOperationsCapable) wrapper;
      }
      if (wrapper instanceof BulkOperationsCapable) {
         bulkCacheWrapper = (BulkOperationsCapable) wrapper;
      }
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

      if (!startOperations()) return Collections.EMPTY_MAP;
      try {
         executeOperations();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      
      Map<String, Object> results = processResults();
      finishOperations();
      return results;
   }

   protected boolean startOperations() {
      try {
         synchronizer.masterPhaseStart();
      } catch (InterruptedException e) {
         return false;
      }
      return true;
   }

   public void destroy() throws Exception {
      cacheWrapper.empty();
      cacheWrapper = null;
      bulkCacheWrapper = null;
   }

   protected boolean isTerminated() {
      return terminated;
   }

   protected Map<String, Object> processResults() {
      Statistics stats = createStatistics();

      for (Stressor stressor : stressors) {
         stats.merge(stressor.getStats());
      }

      Map<String, Object> results = stats.getResultsMap(numThreads, "");
      results.put(Statistics.REQ_PER_SEC, numThreads * stats.getOperationsPerSecond(true));

      log.info("Finished generating report. Test duration is: " + Utils.getNanosDurationString(System.nanoTime() - startNanos));
      return results;
   }

   protected Statistics createStatistics() {
      return statisticsPrototype.copy();
   }

   protected void executeOperations() throws InterruptedException {
      synchronizer.setSlaveCount(numThreads);
      for (int threadIndex = stressors.size(); threadIndex < numThreads; threadIndex++) {
         Stressor stressor = new Stressor(threadIndex, getLogic());
         stressors.add(stressor);
         stressor.start();
      }
      log.info("Cache wrapper info is: " + cacheWrapper.getInfo());
      synchronizer.masterPhaseEnd();
      // wait until all slaves have initialized keys
      synchronizer.masterPhaseStart();
      // nothing to do here
      synchronizer.masterPhaseEnd();
      log.info("Started " + stressors.size() + " stressor threads.");
      // wait until all threads have finished
      synchronizer.masterPhaseStart();
   }
   
   protected void finishOperations() {
      finished = true;
      synchronizer.masterPhaseEnd();
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

   public OperationLogic getLogic() {
      if (fixedKeys && numBytes > 0) {
         throw new IllegalArgumentException("numBytes can be set only for fixedKeys=false");
      } else if (sharedKeys && !fixedKeys) {
         throw new IllegalArgumentException("Cannot use both shared and non-fixed keys - not implemented");
      } else if (!fixedKeys) {
         if (!poolKeys) {
            throw new IllegalArgumentException("Keys have to be pooled with changing set.");
         }
         if (bulkSize != 1 || useAtomics) {
            throw new IllegalArgumentException("Replace/bulk operations on changing set not supported.");
         }
         if (removePercentage > 0) {
            throw new IllegalArgumentException("Removes cannot be configured in when using non-fixed keys");
         }
         return new ChangingSetOperationLogic();
      } else if (bulkSize != 1) {
         if (bulkSize > 1 && bulkSize <= numEntries) {
            if (cacheWrapper instanceof BulkOperationsCapable) {
               if (sharedKeys) {
                  return new BulkOperationLogic(new FixedSetSharedOperationLogic(sharedKeysPool), preferAsyncOperations);
               } else {
                  return new BulkOperationLogic(new FixedSetPerThreadOperationLogic(), preferAsyncOperations);
               }
            } else {
               throw new IllegalArgumentException("Cache wrapper " + cacheWrapper.toString() + " does not support bulk operations.");
            }
         } else {
            throw new IllegalArgumentException("Invalid bulk size, must be 1 < bulkSize(" + bulkSize + ") < numEntries(" + numEntries + ")");
         }
      } else if (useAtomics) {
         if (sharedKeys) {
            throw new IllegalArgumentException("Atomics on shared keys are not supported.");
         } else if (cacheWrapper instanceof AtomicOperationsCapable) {
            if (!poolKeys) {
               log.warn("Keys are not pooled, but last values must be recorded!");
            }
            return new FixedSetAtomicOperationLogic();
         } else {
            throw new IllegalArgumentException("Atomics can be executed only on wrapper which supports atomic operations.");
         }
      } else if (sharedKeys) {
         return new FixedSetSharedOperationLogic(sharedKeysPool);
      } else {
         return new FixedSetPerThreadOperationLogic();
      }
   }

   public void setStatisticsPrototype(Statistics statisticsPrototype) {
      this.statisticsPrototype = statisticsPrototype;
   }

   /* Exception thrown from the request itself - if thrown, the logic
      cannot know whether the request was successful or not and should behave according to that. */
   protected class RequestException extends Exception {
      private RequestException(Throwable cause) {
         super(cause);
      }
   }

   protected interface OperationLogic {
      void init(String bucketId, int threadIndex);
      Object run(Stressor stressor) throws RequestException;
   }

   protected abstract class FixedSetOperationLogic implements OperationLogic {
      private Random r = new Random();

      @Override
      public Object run(Stressor stressor) throws RequestException {
         int randomAction = r.nextInt(100);
         int randomKeyInt = r.nextInt(numEntries - 1);
         Object key = getKey(randomKeyInt, stressor.threadIndex);

         if (randomAction < writePercentage) {
            return stressor.makeRequest(Operation.PUT, key, generateValue(Integer.MAX_VALUE));
         } else if (randomAction < writePercentage + removePercentage) {
            return stressor.makeRequest(Operation.REMOVE, key);
         } else {
            return stressor.makeRequest(Operation.GET, key);
         }
      }

      protected abstract Object getKey(int keyId, int threadIndex);
   }

   protected class FixedSetPerThreadOperationLogic extends FixedSetOperationLogic {
      private ArrayList<Object> pooledKeys = new ArrayList<Object>(numEntries);
      private int myLoadedKeys = 0;

      @Override
      public void init(String bucketId, int threadIndex) {
         if (poolKeys) {
            if (pooledKeys.size() == numEntries) return;
         } else {
            if (myLoadedKeys == numEntries) return;
         }
         KeyGenerator keyGenerator = getKeyGenerator();
         for (int keyIndex = 0; keyIndex < numEntries; keyIndex++) {
            Object key = null;

            if (isLocalBenchmark()) {
               key = keyGenerator.generateKey(threadIndex * numEntries + keyIndex);
            } else {
               key = keyGenerator.generateKey((nodeIndex * numThreads + threadIndex) * numEntries + keyIndex);
            }
            Object value = generateValue(Integer.MAX_VALUE);
            addPooledKey(key, value);
            try {
               cacheWrapper.put(bucketId, key, value);
               long loaded = keysLoaded.incrementAndGet();
               if (loaded % 100000 == 0) {
                  Runtime runtime = Runtime.getRuntime();
                  log.info(String.format("Loaded %d/%d entries (on this node), free %d MB/%d MB",
                        loaded, numEntries * numThreads, runtime.freeMemory() / 1048576, runtime.maxMemory() / 1048576));
               }
            } catch (Throwable e) {
               log.warn("Failed to insert key " + key, e);
            }
         }
      }

      protected void addPooledKey(Object key, Object value) {
         if (poolKeys) {
            pooledKeys.add(key);
         } else {
            myLoadedKeys++;
         }
      }

      protected Object getKey(int keyId, int threadIndex) {
         if (poolKeys) {
            return pooledKeys.get(keyId);
         } else if (isLocalBenchmark()) {
            return keyGenerator.generateKey(threadIndex * numEntries + keyId);
         } else {
            return keyGenerator.generateKey((nodeIndex * numThreads + threadIndex) * numEntries + keyId);
         }
      }
   }

   protected class FixedSetSharedOperationLogic extends FixedSetOperationLogic {

      private ArrayList<Object> sharedKeys;

      public FixedSetSharedOperationLogic(ArrayList<Object> sharedKeys) {
         this.sharedKeys = sharedKeys;
      }

      @Override
      public Object getKey(int keyId, int threadIndex) {
         if (poolKeys) {
            return sharedKeys.get(keyId);
         } else {
            return keyGenerator.generateKey(keyId);
         }
      }

      @Override
      public void init(String bucketId, int threadIndex) {
         if (poolKeys) {
            synchronized (sharedKeys) {
               // no point in doing this in parallel, too much overhead in synchronization
               if (threadIndex == 0) {
                  if (sharedKeys.size() != numEntries) {
                     sharedKeys.clear();
                     KeyGenerator keyGenerator = getKeyGenerator();
                     for (int keyIndex = 0; keyIndex < numEntries; ++keyIndex) {
                        sharedKeys.add(keyGenerator.generateKey(keyIndex));
                     }
                  }
                  sharedKeys.notifyAll();
               } else {
                  while (sharedKeys.size() != numEntries) {
                     try {
                        sharedKeys.wait();
                     } catch (InterruptedException e) {
                     }
                  }
               }
            }
         }
         int loadedEntryCount, keyIndex, loadingThreads;
         if (loadAllKeys) {
            loadedEntryCount = numEntries;
            loadingThreads = numThreads;
            keyIndex = threadIndex;
         } else {
            loadedEntryCount = numEntries / numNodes + (nodeIndex < numEntries % numNodes ? 1 : 0);
            loadingThreads = numThreads * numNodes;
            keyIndex = threadIndex + nodeIndex * numThreads;
         }
         if (threadIndex == 0) {
            log.info(String.format("We have loaded %d keys, expecting %d locally loaded, %d in cache",
                  keysLoaded.get(), loadedEntryCount, cacheWrapper.getLocalSize()));
         }
         if (keysLoaded.get() >= loadedEntryCount) {
            return;
         }
         for (; keyIndex < numEntries; keyIndex += loadingThreads) {
            try {
               cacheWrapper.put(null, getKey(keyIndex, threadIndex), generateValue(Integer.MAX_VALUE));
               long loaded = keysLoaded.incrementAndGet();
               if (loaded % 100000 == 0) {
                  Runtime runtime = Runtime.getRuntime();
                  log.info(String.format("Loaded %d/%d entries (on this node), free %d MB/%d MB",
                        loaded, loadedEntryCount, runtime.freeMemory() / 1048576, runtime.maxMemory() / 1048576));
               }
            } catch (Exception e) {
               log.error("Failed to insert shared key " + keyIndex, e);
            }
         }
      }
   }

   protected class FixedSetAtomicOperationLogic extends FixedSetPerThreadOperationLogic {
      private Map<Object, Object> lastValues = new HashMap<Object, Object>(numEntries);

      @Override
      public Object run(Stressor stressor) throws RequestException {
         int randomAction = r.nextInt(100);
         Object key = getKey(r.nextInt(numEntries - 1), stressor.threadIndex);
         Object lastValue = lastValues.get(key);

         Object newValue = generateValue(Integer.MAX_VALUE);
         int probability = 0;
         if (lastValue == null) {
            lastValues.put(key, newValue);
            return stressor.makeRequest(Operation.PUT_IF_ABSENT_IS_ABSENT, key, newValue);
         } else if (randomAction < (probability += writePercentage)) {
            return stressor.makeRequest(Operation.PUT_IF_ABSENT_NOT_ABSENT, key, newValue, lastValue);
         } else if (randomAction < (probability += removePercentage)) {
            lastValues.remove(key);
            return stressor.makeRequest(Operation.REMOVE_VALID, key, lastValue);
         } else if (randomAction < (probability += removeInvalidPercentage)) {
            return stressor.makeRequest(Operation.REMOVE_INVALID, key, generateValue(Integer.MAX_VALUE));
         } else if (randomAction < (probability += replaceInvalidPercentage)) {
            return stressor.makeRequest(Operation.REPLACE_INVALID, key, generateValue(Integer.MAX_VALUE), newValue);
         } else {
            lastValues.put(key, newValue);
            return stressor.makeRequest(Operation.REPLACE_VALID, key, lastValue, newValue);
         }
      }

      @Override
      protected void addPooledKey(Object key, Object value) {
         super.addPooledKey(key, value);
         lastValues.put(key, value);
      }
   }

   protected class BulkOperationLogic implements OperationLogic {
      private final FixedSetOperationLogic initLogic;
      private final Operation putOperation;
      private final Operation removeOperation;
      private final Operation getOperation;

      public BulkOperationLogic(FixedSetOperationLogic initLogic, boolean preferAsyncOperations) {
         this.initLogic = initLogic;
         if (preferAsyncOperations) {
            putOperation = Operation.PUT_ALL_VIA_ASYNC;
            removeOperation = Operation.REMOVE_ALL_VIA_ASYNC;
            getOperation = Operation.GET_ALL_VIA_ASYNC;
         } else {
            putOperation = Operation.PUT_ALL;
            removeOperation = Operation.REMOVE_ALL;
            getOperation = Operation.GET_ALL;
         }
      }

      @Override
      public void init(String bucketId, int threadIndex) {
         initLogic.init(bucketId, threadIndex);
      }

      @Override
      public Object run(Stressor stressor) throws RequestException {
         int randomAction = r.nextInt(100);
         if (randomAction < writePercentage) {
            Map<Object, Object> map = new HashMap<Object, Object>(bulkSize);
            for (int i = 0; i < bulkSize;) {
               Object key = initLogic.getKey(r.nextInt(numEntries - 1), stressor.threadIndex);
               if (!map.containsKey(key)) {
                  map.put(key, generateValue(Integer.MAX_VALUE));
                  ++i;
               }
            }
            return stressor.makeRequest(putOperation, map);
         } else {
            Set<Object> set = new HashSet<Object>(bulkSize);
            for (int i = 0; i < bulkSize; ) {
               Object key = initLogic.getKey(r.nextInt(numEntries - 1), stressor.threadIndex);
               if (!set.contains(key)) {
                  set.add(key);
                  ++i;
               }
            }
            if (randomAction < writePercentage + removePercentage) {
               return stressor.makeRequest(removeOperation, set);
            } else {
               return stressor.makeRequest(getOperation, set);
            }
         }
      }
   }

   private static class KeyWithRemovalTime implements Comparable<KeyWithRemovalTime> {
      public final Object key;
      public final long removeTimestamp;
      public int valueSize;

      public KeyWithRemovalTime(Object key, long removeTimestamp, int valueSize) {
         this.key = key;
         this.removeTimestamp = removeTimestamp;
         this.valueSize = valueSize;
      }

      @Override
      public int compareTo(KeyWithRemovalTime o) {
         if (removeTimestamp < o.removeTimestamp) return -1;
         if (removeTimestamp > o.removeTimestamp) return 1;
         if (key == null || o.key == null) return 0;
         if (key.equals(o.key)) return 0;
         return -1;
      }
   }

   protected class ChangingSetOperationLogic implements OperationLogic {
      private TreeSet<KeyWithRemovalTime> scheduledKeys = new TreeSet<KeyWithRemovalTime>();
      private Random r = new Random();
      private long currentLoad;
      private long changeId = 0;

      @Override
      public void init(String bucketId, int threadIndex) {
         keysLoaded.compareAndSet(0, nodeIndex);
      }

      @Override
      public Object run(Stressor stressor) throws RequestException {
         KeyWithRemovalTime pair;
         long timestamp = System.currentTimeMillis();
         if (!scheduledKeys.isEmpty() && scheduledKeys.first().removeTimestamp <= timestamp) {
            pair = scheduledKeys.pollFirst();
            Object value;
            try {
               value = stressor.makeRequest(Operation.REMOVE, pair.key);
            } catch (RequestException e) {
               scheduledKeys.add(pair);
               return null;
            }
            if (value == null) {
               log.error("REMOVE: Value for key " + pair.key + " is null!");
            } else {
               currentLoad -= sizeOf(value);
            }
            if (++changeId % 1000 == 0) {
               log.info(String.format("Current load: %.2f MB", currentLoad / 1048576d));
            }
            return value;
         } else if (r.nextInt(100) >= writePercentage && scheduledKeys.size() > 0) {
            // we cannot get random access to PriorityQueue and there is no SortedList or another appropriate structure
            Object key = getRandomKey(timestamp);
            Object value = stressor.makeRequest(Operation.GET, key);
            if (value == null) {
               log.error("GET: Value for key " + key + " is null!");
            }
            return value;
         } else {
            Object value = generateValue(numBytes > 0 ? (int) Math.min(numBytes - currentLoad, Integer.MAX_VALUE) : Integer.MAX_VALUE);
            int size = sizeOf(value);
            if ((numBytes > 0 && currentLoad < numBytes) || scheduledKeys.size() < numEntries) {
               long keyIndex = keysLoaded.getAndAdd(numNodes);
               pair = new KeyWithRemovalTime(getKeyGenerator().generateKey(keyIndex), getRandomTimestamp(timestamp), size);
               scheduledKeys.add(pair);
               currentLoad += size;
            } else {
               pair = getRandomPair(timestamp);
               currentLoad += size - pair.valueSize;
            }
            if (++changeId % 1000 == 0) {
               log.info(String.format("Current load: %.2f MB", currentLoad / 1048576d));
            }
            try {
               return stressor.makeRequest(Operation.PUT, pair.key, value);
            } catch (RequestException e) {
               currentLoad -= size;
               scheduledKeys.remove(pair);
               for (;;) {
                  try {
                     return stressor.makeRequest(Operation.REMOVE, pair.key);
                  } catch (RequestException e1) {
                  }
               }
            }
         }
      }

      private long getRandomTimestamp(long current) {
         // ~sqrt probability for 1 - maxRoot^2
         final long maxRoot = (long) Math.sqrt((double) entryLifespan);
         long rand = r.nextLong() % maxRoot;
         return current + rand * rand + r.nextLong() % (2*maxRoot - 2) + 1;
      }

      private Object getRandomKey(long timestamp) {
         // GET is executed only if there is some key present
         KeyWithRemovalTime pair = getRandomPair(timestamp);
         return pair.key;
      }

      private KeyWithRemovalTime getRandomPair(long timestamp) {
         KeyWithRemovalTime pair = scheduledKeys.floor(new KeyWithRemovalTime(null, getRandomTimestamp(timestamp), 0));
         return pair != null ? pair : scheduledKeys.first();
      }
   }

   protected class Stressor extends Thread {
      private int threadIndex;
      private final String bucketId;
      private int txRemainingOperations = 0;
      private long transactionDuration = 0;
      private Statistics stats;
      private OperationLogic logic;
      private boolean useTransactions = isUseTransactions();

      public Stressor(int threadIndex, OperationLogic logic) {
         super("Stressor-" + threadIndex);         
         this.threadIndex = threadIndex;
         this.logic = logic;
         this.bucketId = useBuckets ? "bucket_" + threadIndex : null;
      }

      @Override
      public void run() {
         try {
            for (;;) {
               synchronizer.slavePhaseStart();
               if (finished) {
                  synchronizer.slavePhaseEnd();
                  break;
               }
               if (!terminated) {
                  logic.init(bucketId, threadIndex);
               }
               stats = createStatistics();
               synchronizer.slavePhaseEnd();
               synchronizer.slavePhaseStart();
               try {
                  if (!terminated) {
                     log.trace("Starting thread: " + getName());
                     runInternal();
                  }
               } catch (Exception e) {
                  terminated = true;
                  log.error("Unexpected error in stressor!", e);
               } finally {
                  synchronizer.slavePhaseEnd();
               }
            }
         } catch (Exception e) {
            log.error("Unexpected error in stressor!", e);
         }
      }
      
      private void runInternal() {
         int i = 0;
         while (completion.moreToRun()) {
            Object result = null;
            try {
               result = logic.run(this);
            } catch (RequestException e) {
               // the exception was already logged in makeRequest
            }
            i++;
            completion.logProgress(i, result, threadIndex);
         }

         if (txRemainingOperations > 0) {
            try {
               long endTxTime = endTransaction();
               stats.registerRequest(transactionDuration + endTxTime, 0, Operation.TRANSACTION);
            } catch (TransactionException e) {
               stats.registerError(transactionDuration + e.getOperationDuration(), 0, Operation.TRANSACTION);
            }
            transactionDuration = 0;
         }
      }

      public Object makeRequest(Operation operation, Object... keysAndValues) throws RequestException {
         long startTxTime = 0;
         if (useTransactions && txRemainingOperations <= 0) {
            try {
               startTxTime = startTransaction();
               transactionDuration = startTxTime;
               txRemainingOperations = transactionSize;
            } catch (TransactionException e) {
               stats.registerError(e.getOperationDuration(), 0, Operation.TRANSACTION);
               return null;
            }
         }

         Object result = null;
         boolean successfull = true;
         Exception exception = null;
         long start = System.nanoTime();
         long operationDuration;
         try {
            switch (operation) {
               case GET:
               case GET_NULL:
                  result = cacheWrapper.get(bucketId, keysAndValues[0]);
                  operation = (result != null ? Operation.GET : Operation.GET_NULL);
                  break;
               case PUT:
                  cacheWrapper.put(bucketId, keysAndValues[0], keysAndValues[1]);
                  break;
               case QUERY:
                  result = ((Queryable) cacheWrapper).executeQuery((Map<String, Object>) keysAndValues[0]);
                  break;
               case REMOVE:
                  result = cacheWrapper.remove(bucketId, keysAndValues[0]);
                  break;
               case REMOVE_VALID:
                  successfull = atomicCacheWrapper.remove(bucketId, keysAndValues[0], keysAndValues[1]);
                  break;
               case REMOVE_INVALID:
                  successfull = !atomicCacheWrapper.remove(bucketId, keysAndValues[0], keysAndValues[1]);
                  break;
               case PUT_IF_ABSENT_IS_ABSENT:
                  result = atomicCacheWrapper.putIfAbsent(bucketId, keysAndValues[0], keysAndValues[1]);
                  successfull = result == null;
                  break;
               case PUT_IF_ABSENT_NOT_ABSENT:
                  result = atomicCacheWrapper.putIfAbsent(bucketId, keysAndValues[0], keysAndValues[1]);
                  successfull = keysAndValues[2].equals(result);
                  break;
               case REPLACE_VALID:
                  successfull = atomicCacheWrapper.replace(bucketId, keysAndValues[0], keysAndValues[1], keysAndValues[2]);
                  break;
               case REPLACE_INVALID:
                  successfull = !atomicCacheWrapper.replace(bucketId, keysAndValues[0], keysAndValues[1], keysAndValues[2]);
                  break;
               case GET_ALL:
               case GET_ALL_VIA_ASYNC:
                  result = bulkCacheWrapper.getAll(bucketId, (Set<Object>) keysAndValues[0], operation == Operation.GET_ALL_VIA_ASYNC);
                  break;
               case PUT_ALL:
               case PUT_ALL_VIA_ASYNC:
                  bulkCacheWrapper.putAll(bucketId, (Map<Object, Object>) keysAndValues[0], operation == Operation.PUT_ALL_VIA_ASYNC);
                  break;
               case REMOVE_ALL:
               case REMOVE_ALL_VIA_ASYNC:
                  result = bulkCacheWrapper.removeAll(bucketId, (Set<Object>) keysAndValues[0], operation == Operation.REMOVE_ALL_VIA_ASYNC);
                  break;
               default:
                  throw new IllegalArgumentException();
            }
            operationDuration = System.nanoTime() - start;
            txRemainingOperations--;
         } catch (Exception e) {
            operationDuration = System.nanoTime() - start;
            log.warn("Error in request", e);
            successfull = false;
            txRemainingOperations = 0;
            exception = e;
         }
         transactionDuration += operationDuration;

         long endTxTime = 0;
         if (useTransactions && txRemainingOperations <= 0) {
            try {
               endTxTime = endTransaction();
               stats.registerRequest(transactionDuration + endTxTime, 0, Operation.TRANSACTION);
            } catch (TransactionException e) {
               endTxTime = e.getOperationDuration();
               stats.registerError(transactionDuration + endTxTime, 0, Operation.TRANSACTION);
            }
         }
         if (successfull) {
            stats.registerRequest(operationDuration, startTxTime + endTxTime, operation);
         } else {
            stats.registerError(operationDuration, startTxTime + endTxTime, operation);
         }
         if (exception != null) {
            throw new RequestException(exception);
         }
         return result;
      }

      public Statistics getStats() {
         return stats;
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

   public int getNumRequests() {
      return numRequests;
   }
      
   public int getNumThreads() {
      return numThreads;
   }
   

   public int getNumEntries() {
      return numEntries;
   }

   public void setNumThreads(int numOfThreads) {
      this.numThreads = numOfThreads;
   }

   protected Object generateValue(int maxValueSize) {
      int size = entrySize.next(r);
      size = Math.min(size, maxValueSize);
      byte[] array = new byte[size];
      r.nextBytes(array);
      if (useAtomics) {
         // this has a few bytes over but supports equals etc.
         return new ByteArrayWrapper(array);
      } else {
         return array;
      }
   }

   protected int sizeOf(Object value) {
      if (value instanceof byte[]) {
         return ((byte[]) value).length;
      } else if (value instanceof ByteArrayWrapper) {
         return ((ByteArrayWrapper) value).array.length;
      } else {
         throw new IllegalArgumentException("Cannot find size of " + value);
      }
   }

   public void setNodeIndex(int nodeIndex, int numNodes) {
      this.nodeIndex = nodeIndex;
      this.numNodes = numNodes;
   }

   public KeyGenerator getKeyGenerator() {
      if (keyGenerator == null) {
         log.info("Using key generator " + keyGeneratorClass + ", param " + keyGeneratorParam);
         keyGenerator = (KeyGenerator) Utils.instantiate(keyGeneratorClass);
         keyGenerator.init(keyGeneratorParam);
      }
      return keyGenerator;
   }

   public boolean isUseTransactions() {
      return useTransactions == null ? cacheWrapper.isTransactional(null) : useTransactions;
   }

   public long getDurationMillis() {
      return durationMillis;
   }

   public void setDurationMillis(long durationMillis) {
      this.durationMillis = durationMillis;
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
         return requestsLeft.getAndDecrement() > 0;
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
            ", useTransactions=" + isUseTransactions() +
            ", transactionSize=" + transactionSize +
            ", commitTransactions=" + commitTransactions +
            ", durationMillis=" + durationMillis +
            "}";
   }

   /* Because byte[].equals compares only pointers */
   private static class ByteArrayWrapper implements Serializable {
      private byte[] array;
      private transient int hashCode = 0;

      public ByteArrayWrapper(byte[] array) {
         this.array = array;
      }

      @Override
      public int hashCode() {
         if (hashCode == 0) {
            hashCode = 42 + Arrays.hashCode(array);
            if (hashCode == 0) hashCode = 42;
         }
         return hashCode;
      }

      @Override
      public String toString() {
         return String.format("ByteArray[%d](%db)", hashCode(), array.length);
      }

      @Override
      public boolean equals(Object obj) {
         if (obj instanceof ByteArrayWrapper) {
            return Arrays.equals(array, ((ByteArrayWrapper) obj).array);
         }
         return false;
      }
   }
}

