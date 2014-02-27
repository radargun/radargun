package org.radargun.stages.cache.background;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.radargun.CacheWrapper;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.features.Debugable;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class LogChecker extends Thread {
   protected static final Log log = LogFactory.getLog(LogChecker.class);
   protected static final boolean trace = log.isTraceEnabled();
   protected static final long UNSUCCESSFUL_CHECK_MIN_DELAY_MS = 10;
   protected final String bucketId;
   protected final KeyGenerator keyGenerator;
   protected final int slaveIndex;
   protected final long logCounterUpdatePeriod;
   protected final Pool pool;
   protected final CacheWrapper cacheWrapper;
   protected volatile boolean terminate = false;

   public LogChecker(String name, BackgroundOpsManager manager, Pool logCheckerPool) {
      super(name);
      bucketId = manager.getBucketId();
      keyGenerator = manager.getKeyGenerator();
      slaveIndex = manager.getSlaveIndex();
      logCounterUpdatePeriod = manager.getLogCounterUpdatePeriod();
      pool = logCheckerPool;
      cacheWrapper = manager.getCacheWrapper();
   }

   public static String checkerKey(int checkerSlaveId, int slaveAndThreadId) {
      return String.format("checker_%d_%d", checkerSlaveId, slaveAndThreadId);
   }

   public static String ignoredKey(int checkerSlaveId, int slaveAndThreadId) {
      return String.format("ignored_%d_%d", checkerSlaveId, slaveAndThreadId);
   }

   public static String lastOperationKey(int slaveAndThreadId) {
      return String.format("stressor_%d", slaveAndThreadId);
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
               Object last = cacheWrapper.get(bucketId, lastOperationKey(record.getThreadId()));
               if (last != null) {
                  record.setLastStressorOperation(((LastOperation) last).getOperationId());
               }
            }
            if (record.getOperationId() == 0) {
               Object last = cacheWrapper.get(bucketId, checkerKey(slaveIndex, record.getThreadId()));
               if (last != null) {
                  LastOperation lastCheck = (LastOperation) last;
                  record = newRecord(record, lastCheck.getOperationId(), lastCheck.getSeed());
               }
               Object ignored = cacheWrapper.get(bucketId, ignoredKey(slaveIndex, record.getThreadId()));
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
            Object value = findValue(record);
            if (containsOperation(value, record)) {
               if (trace) {
                  log.trace(String.format("Found operation %d for thread %d", record.getOperationId(), record.getThreadId()));
               }
               if (record.getOperationId() % logCounterUpdatePeriod == 0) {
                  cacheWrapper.put(bucketId, checkerKey(slaveIndex, record.getThreadId()),
                        new LastOperation(record.getOperationId(), Utils.getRandomSeed(record.rand)));
               }
               record.next();
               record.setLastUnsuccessfulCheckTimestamp(Long.MIN_VALUE);
               pool.reportStoredOperation();
            } else {
               if (record.getLastStressorOperation() >= record.getOperationId()) {
                  // one more check to see whether some operations should not be ignored
                  Object ignored = cacheWrapper.get(bucketId, ignoredKey(slaveIndex, record.getThreadId()));
                  if (ignored != null && record.getOperationId() <= (Long) ignored) {
                     log.debug(String.format("Operations %d - %d for thread %d are ignored.", record.getOperationId(), ignored, record.threadId));
                     while (record.getOperationId() <= (Long) ignored) {
                        record.next();
                     }
                     continue;
                  }

                  log.error(String.format("Missing operation %d for thread %d on key %d (%s) %s",
                        record.getOperationId(), record.getThreadId(), record.getKeyId(),
                        keyGenerator.generateKey(record.getKeyId()),
                        value == null ? " - entry was completely lost" : ""));
                  if (trace) {
                     log.trace("Not found in " + value);
                  }
                  pool.reportMissingOperation();
                  if (cacheWrapper instanceof Debugable) {
                     ((Debugable) cacheWrapper).debugInfo(bucketId);
                     ((Debugable) cacheWrapper).debugKey(bucketId, keyGenerator.generateKey(record.getKeyId()));
                     ((Debugable) cacheWrapper).debugKey(bucketId, keyGenerator.generateKey(~record.getKeyId()));
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

   public static abstract class Pool {
      private final int totalThreads;
      private final AtomicReferenceArray<AbstractStressorRecord> allRecords;
      private final ConcurrentLinkedQueue<AbstractStressorRecord> records = new ConcurrentLinkedQueue<AbstractStressorRecord>();
      private final BackgroundOpsManager manager;
      private final AtomicLong missingOperations = new AtomicLong();
      private volatile long lastStoredOperationTimestamp = Long.MIN_VALUE;


      public Pool(int numThreads, int numSlaves, BackgroundOpsManager manager) {
         totalThreads = numThreads * numSlaves;
         allRecords = new AtomicReferenceArray<AbstractStressorRecord>(totalThreads);
         this.manager = manager;
      }

      public long getMissingOperations() {
         return missingOperations.get();
      }

      public void reportMissingOperation() {
         missingOperations.incrementAndGet();
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
         allRecords.set(record.getThreadId(), record);
      }

      public String waitUntilChecked(long timeout) {
         for (int i = 0; i < totalThreads; ++i) {
            AbstractStressorRecord record = allRecords.get(i);
            if (record == null) continue;
            try {
               LastOperation lastOperation = (LastOperation) manager.getCacheWrapper().get(manager.getBucketId(), lastOperationKey(record.getThreadId()));
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
      protected long currentOp = -1;
      private long lastStressorOperation = -1;
      private long lastUnsuccessfulCheckTimestamp = Long.MIN_VALUE;

      public AbstractStressorRecord(long seed, int threadId, long operationId) {
         this.rand = Utils.setRandomSeed(new Random(0), seed);
         this.threadId = threadId;
         this.currentOp = operationId;
      }

      public AbstractStressorRecord(Random rand, int threadId) {
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
   }
}
