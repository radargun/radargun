package org.radargun.stages.cache.background;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheListeners;
import org.radargun.traits.Debugable;
import org.radargun.utils.Utils;

/**
 * Log checkers control that all operations executed by stressors are persisted in the log values.
 * Each node checks all writes from all stressors, but there's not a one-to-one stressor-checker
 * relation. Instead, each node holds a pool of checker threads and a shared data structure
 * with records about each stressor. All records are iterated through in a round-robin fashion
 * by the checker threads.
 *
 * When the checkers are dead on particular node, this node cannot check the stressors. For some
 * scenarios this is limiting - therefore, stressors may be configured to unwind the log values
 * even if the old records are not checked. Then, it has to notify the checker about this action
 * via ignored_* key, to prevent it from failing the test.
 *
 * @see AbstractLogLogic
 * @see Stressor
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class LogChecker extends Thread {
   protected static final Log log = LogFactory.getLog(LogChecker.class);
   protected static final boolean trace = log.isTraceEnabled();
   protected static final long UNSUCCESSFUL_CHECK_MIN_DELAY_MS = 10;
   protected static final String LAST_OPERATION_PREFIX = "stressor_";
   protected final KeyGenerator keyGenerator;
   protected final int slaveIndex;
   protected final long logCounterUpdatePeriod;
   protected final Pool pool;
   protected final BasicOperations.Cache basicCache;
   protected final Debugable.Cache debugableCache;
   protected volatile boolean terminate = false;

   public LogChecker(String name, BackgroundOpsManager manager, Pool logCheckerPool) {
      super(name);
      keyGenerator = manager.getKeyGenerator();
      slaveIndex = manager.getSlaveIndex();
      logCounterUpdatePeriod = manager.getLogLogicConfiguration().getCounterUpdatePeriod();
      pool = logCheckerPool;
      this.basicCache = manager.getBasicCache();
      this.debugableCache = manager.getDebugableCache();
   }

   public static String checkerKey(int checkerSlaveId, int slaveAndThreadId) {
      return String.format("checker_%d_%d", checkerSlaveId, slaveAndThreadId);
   }

   public static String ignoredKey(int checkerSlaveId, int slaveAndThreadId) {
      return String.format("ignored_%d_%d", checkerSlaveId, slaveAndThreadId);
   }

   public static String lastOperationKey(int slaveAndThreadId) {
      return String.format(LAST_OPERATION_PREFIX + "%d", slaveAndThreadId);
   }

   public void requestTerminate() {
      terminate = true;
   }

   @Override
   public void run() {
      int delayedKeys = 0;
      while (!terminate) {
         AbstractStressorRecord record = null;
         try {
            if (delayedKeys > pool.getTotalThreads()) {
               Thread.sleep(UNSUCCESSFUL_CHECK_MIN_DELAY_MS);
            }
            record = pool.take();
            if (System.currentTimeMillis() < record.getLastUnsuccessfulCheckTimestamp() + UNSUCCESSFUL_CHECK_MIN_DELAY_MS) {
               delayedKeys++;
               continue;
            }
            delayedKeys = 0;
            if (record.getLastUnsuccessfulCheckTimestamp() > Long.MIN_VALUE) {
               // the last check was unsuccessful -> grab lastOperation BEFORE the value to check if we've lost that
               Object last = basicCache.get(lastOperationKey(record.getThreadId()));
               if (last != null) {
                  record.setLastStressorOperation(((LastOperation) last).getOperationId());
               }
            }
            if (record.getOperationId() == 0) {
               Object last = basicCache.get(checkerKey(slaveIndex, record.getThreadId()));
               if (last != null) {
                  LastOperation lastCheck = (LastOperation) last;
                  record = newRecord(record, lastCheck.getOperationId(), lastCheck.getSeed());
               }
               Object ignored = basicCache.get(ignoredKey(slaveIndex, record.getThreadId()));
               if (ignored != null && record.getOperationId() <= (Long) ignored) {
                  log.debug(String.format("Ignoring operations %d - %d for thread %d", record.getOperationId(), ignored, record.getThreadId()));
                  while (record.getOperationId() <= (Long) ignored) {
                     record.next();
                  }
               }
               if (record.getOperationId() != 0) {
                  log.debug(String.format("Check for thread %d continues from operation %d",
                     record.getThreadId(), record.getOperationId()));
               }
            }
            if (trace) {
               log.trace(String.format("Checking operation %d for thread %d on key %d (%s)",
                     record.getOperationId(), record.getThreadId(), record.getKeyId(), keyGenerator.generateKey(record.getKeyId())));
            }
            boolean notification = record.hasNotification(record.getOperationId());
            Object value = findValue(record);
            boolean contains = containsOperation(value, record);
            if (notification && contains) {
               if (trace) {
                  log.trace(String.format("Found operation %d for thread %d", record.getOperationId(), record.getThreadId()));
               }
               if (record.getOperationId() % logCounterUpdatePeriod == 0) {
                  basicCache.put(checkerKey(slaveIndex, record.getThreadId()),
                        new LastOperation(record.getOperationId(), Utils.getRandomSeed(record.rand)));
               }
               record.next();
               record.setLastUnsuccessfulCheckTimestamp(Long.MIN_VALUE);
               pool.reportStoredOperation();
            } else {
               if (record.getLastStressorOperation() >= record.getOperationId()) {
                  // one more check to see whether some operations should not be ignored
                  Object ignored = basicCache.get(ignoredKey(slaveIndex, record.getThreadId()));
                  if (ignored != null && record.getOperationId() <= (Long) ignored) {
                     log.debug(String.format("Operations %d - %d for thread %d are ignored.", record.getOperationId(), ignored, record.threadId));
                     while (record.getOperationId() <= (Long) ignored) {
                        record.next();
                     }
                     continue;
                  }

                  if (!notification) {
                     log.error(String.format("Missing notification for operation %d for thread %d on key %d (%s), required for %d, notified for %s",
                           record.getOperationId(), record.getThreadId(), record.getKeyId(),
                           keyGenerator.generateKey(record.getKeyId()), record.requireNotify, record.notifiedOps));
                     pool.reportMissingNotification();
                  }
                  if (!contains) {
                     log.error(String.format("Missing operation %d for thread %d on key %d (%s) %s",
                           record.getOperationId(), record.getThreadId(), record.getKeyId(),
                           keyGenerator.generateKey(record.getKeyId()),
                           value == null ? " - entry was completely lost" : ""));
                     if (trace) {
                        log.trace("Not found in " + value);
                     }
                     pool.reportMissingOperation();
                     if (debugableCache != null) {
                        debugableCache.debugInfo();
                        debugableCache.debugKey(keyGenerator.generateKey(record.getKeyId()));
                        debugableCache.debugKey(keyGenerator.generateKey(~record.getKeyId()));
                     }
                  }
                  record.next();
               } else {
                  record.setLastUnsuccessfulCheckTimestamp(System.currentTimeMillis());
               }
            }
         } catch (Exception e) {
            log.error("Cannot check value " + record.getKeyId(), e);
         } finally {
            if (record == null) {
               try {
                  Thread.sleep(100);
               } catch (InterruptedException e) {
                  Thread.interrupted();
               }
            } else {
               pool.add(record);
            }
         }
      }
   }

   protected abstract AbstractStressorRecord newRecord(AbstractStressorRecord record, long operationId, long seed);

   protected abstract Object findValue(AbstractStressorRecord record) throws Exception;

   protected abstract boolean containsOperation(Object value, AbstractStressorRecord record);

   public static abstract class Pool implements CacheListeners.UpdatedListener, CacheListeners.CreatedListener {
      private final int totalThreads;
      private final AtomicReferenceArray<AbstractStressorRecord> allRecords;
      private final ConcurrentLinkedQueue<AbstractStressorRecord> records = new ConcurrentLinkedQueue<AbstractStressorRecord>();
      private final BackgroundOpsManager manager;
      private final AtomicLong missingOperations = new AtomicLong();
      private final AtomicLong missingNotifications = new AtomicLong();
      private final BasicOperations.Cache cache;
      private volatile long lastStoredOperationTimestamp = Long.MIN_VALUE;

      public Pool(int numThreads, int numSlaves, BackgroundOpsManager manager) {
         totalThreads = numThreads * numSlaves;
         allRecords = new AtomicReferenceArray<AbstractStressorRecord>(totalThreads);
         log.trace("Pool will contain " + allRecords.length() + records);
         this.manager = manager;
         this.cache = manager.getBasicCache();
      }

      protected void registerListeners() {
         if (!manager.getLogLogicConfiguration().isCheckNotifications()) {
            return;
         }
         CacheListeners listeners = manager.getListeners();
         if (listeners == null) {
            throw new IllegalArgumentException("Service does not support cache listeners");
         }
         Collection<CacheListeners.Type> supported = listeners.getSupportedListeners();
         if (!supported.containsAll(Arrays.asList(CacheListeners.Type.CREATED, CacheListeners.Type.UPDATED))) {
            throw new IllegalArgumentException("Service does not support required listener types; supported are: " + supported);
         }
         String cacheName = manager.getGeneralConfiguration().getCacheName();
         manager.getListeners().addCreatedListener(cacheName, this);
         manager.getListeners().addUpdatedListener(cacheName, this);
      }

      public long getMissingOperations() {
         return missingOperations.get();
      }

      public long getMissingNotifications() {
         return missingNotifications.get();
      }

      public void reportMissingOperation() {
         missingOperations.incrementAndGet();
      }

      public void reportMissingNotification() {
         missingNotifications.incrementAndGet();
      }

      public int getTotalThreads() {
         return totalThreads;
      }

      public void reportStoredOperation() {
         lastStoredOperationTimestamp = System.currentTimeMillis();
      }

      public long getLastStoredOperationTimestamp() {
         return lastStoredOperationTimestamp;
      }

      public AbstractStressorRecord take() {
         return records.poll();
      }

      public void add(AbstractStressorRecord record) {
         records.add(record);
      }

      public void addNew(AbstractStressorRecord record) {
         records.add(record);
         allRecords.set(record.getThreadId(), record);
      }

      public String waitUntilChecked(long timeout) {
         for (int i = 0; i < totalThreads; ++i) {
            AbstractStressorRecord record = allRecords.get(i);
            if (record == null) continue;
            try {
               LastOperation lastOperation = (LastOperation) cache.get(lastOperationKey(record.getThreadId()));
               if (lastOperation == null) {
                  log.trace("Thread " + record.getThreadId() + " has no recorded operation.");
               } else {
                  record.setLastStressorOperation(lastOperation.getOperationId());
               }
            } catch (Exception e) {
               log.error("Failed to read last operation key for thread " + record.getThreadId(), e);
            }
         }
         for (;;) {
            boolean allChecked = true;
            for (int i = 0; i < totalThreads; ++i) {
               AbstractStressorRecord record = allRecords.get(i);
               if (record == null) continue;
               if (record.getOperationId() <= record.getLastStressorOperation()) {
                  if (log.isTraceEnabled()) {
                     log.trace(String.format("Currently checked operation for thread %d is %d (key id %08X), last written is %d",
                           record.getThreadId(), record.getOperationId(), record.getKeyId(), record.getLastStressorOperation()));
                  }
                  allChecked = false;
                  break;
               }
            }
            if (lastStoredOperationTimestamp + timeout < System.currentTimeMillis()) {
               String error = "Waiting for checkers timed out after " + (System.currentTimeMillis() - lastStoredOperationTimestamp) + " ms";
               log.error(error);
               return error;
            }
            if (allChecked) {
               StringBuilder sb = new StringBuilder("All checks OK: ");
               for (int i = 0; i < totalThreads; ++i) {
                  AbstractStressorRecord record = allRecords.get(i);
                  if (record == null) continue;
                  sb.append(record.getThreadId()).append("# ")
                        .append(record.getOperationId()).append(" (")
                        .append(record.getLastStressorOperation()).append("), ");
               }
               log.debug(sb.toString());
               return null;
            }
            try {
               Thread.sleep(1000);
            } catch (InterruptedException e) {
               log.error("Interrupted waiting for checkers.", e);
               return e.toString();
            }
         }
      }

      protected void notify(int threadId, long operationId, Object key) {
         AbstractStressorRecord record = allRecords.get(threadId);
         record.notify(operationId, key);
      }

      protected void requireNotify(int threadId, long operationId) {
         AbstractStressorRecord record = allRecords.get(threadId);
         record.requireNotify(operationId);
      }

      protected void modified(Object key, Object value) {
         if (key instanceof String && ((String) key).startsWith(LAST_OPERATION_PREFIX)) {
            int threadId = Integer.parseInt(((String) key).substring(LAST_OPERATION_PREFIX.length()));
            LastOperation last = (LastOperation) value;
            requireNotify(threadId, last.getOperationId() + 1);
         }
      }
   }

   public static class LastOperation implements Serializable {
      private long operationId;
      private long seed;

      public LastOperation(long operationId, long seed) {
         this.operationId = operationId;
         this.seed = seed;
      }

      public long getOperationId() {
         return operationId;
      }

      public long getSeed() {
         return seed;
      }

      @Override
      public String toString() {
         return String.format("LastOperation{operationId=%d, seed=%016X}", operationId, seed);
      }
   }

   protected abstract static class AbstractStressorRecord {
      protected final Random rand;
      protected final int threadId;
      protected long currentKeyId;
      protected volatile long currentOp = -1;
      private long lastStressorOperation = -1;
      private long lastUnsuccessfulCheckTimestamp = Long.MIN_VALUE;
      private Set<Long> notifiedOps = new HashSet<Long>();
      private long requireNotify = Long.MAX_VALUE;

      public AbstractStressorRecord(long seed, int threadId, long operationId) {
         log.trace("Initializing record random with " + seed);
         this.rand = Utils.setRandomSeed(new Random(0), seed);
         this.threadId = threadId;
         this.currentOp = operationId;
      }

      public AbstractStressorRecord(Random rand, int threadId) {
         log.trace("Initializing record random with " + Utils.getRandomSeed(rand));
         this.rand = rand;
         this.threadId = threadId;
      }

      public abstract void next();

      public int getThreadId() {
         return threadId;
      }

      public long getLastStressorOperation() {
         return lastStressorOperation;
      }

      public void setLastStressorOperation(long lastStressorOperation) {
         this.lastStressorOperation = lastStressorOperation;
      }

      public long getLastUnsuccessfulCheckTimestamp() {
         return lastUnsuccessfulCheckTimestamp;
      }

      public void setLastUnsuccessfulCheckTimestamp(long lastUnsuccessfulCheckTimestamp) {
         this.lastUnsuccessfulCheckTimestamp = lastUnsuccessfulCheckTimestamp;
      }

      public long getKeyId() {
         return currentKeyId;
      }

      public long getOperationId() {
         return currentOp;
      }

      public synchronized void notify(long operationId, Object key) {
         if (operationId < currentOp || !notifiedOps.add(operationId)) {
            log.warn("Duplicit notification for operation " + operationId + " on key " + key);
         }
      }

      public synchronized void discardNotification(long operationId) {
         notifiedOps.remove(operationId);
         // temporary:
         for (long op : notifiedOps) {
            if (op < operationId) log.error("Old operation " + op + " in " + notifiedOps);
         }
      }

      public synchronized void requireNotify(long operationId) {
         if (operationId < requireNotify) {
            requireNotify = operationId;
         }
      }

      public synchronized boolean hasNotification(long operationId) {
         if (operationId < requireNotify) return true;
         return notifiedOps.contains(operationId);
      }
   }
}
