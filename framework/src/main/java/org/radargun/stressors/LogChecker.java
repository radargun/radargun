package org.radargun.stressors;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
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

   public static String lastOperationKey(int slaveAndThreadId) {
      return String.format("stressor_%d", slaveAndThreadId);
   }

   public void requestTerminate() {
      terminate = true;
   }

   @Override
   public void run() {
      while (!terminate) {
         AbstractStressorRecord record = null;
         int delayedKeys = 0;
         try {
            if (delayedKeys > pool.getTotalThreads()) {
               Thread.sleep(UNSUCCESSFUL_CHECK_MIN_DELAY_MS);
            }
            record = pool.take();
            if (System.currentTimeMillis() < record.getLastUnsuccessfulCheckTimestamp() + UNSUCCESSFUL_CHECK_MIN_DELAY_MS) {
               delayedKeys++;
               continue;
            }
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
                  log.debug(String.format("Check for thread %d continues from operation %d",
                        record.getThreadId(), lastCheck.getOperationId() + 1));
                  record = newRecord(record, lastCheck.getOperationId(), lastCheck.getSeed());
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
                  log.error(String.format("Missing operation %d for thread %d on key %d (%s) %s",
                        record.getOperationId(), record.getThreadId(), record.getKeyId(),
                        keyGenerator.generateKey(record.getKeyId()),
                        value == null ? " - entry was completely lost" : ""));
                  if (trace) {
                     log.trace("Not found in " + value);
                  }
                  pool.reportMissingOperation();
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
      protected final int totalThreads;
      protected final ConcurrentLinkedQueue<AbstractStressorRecord> records = new ConcurrentLinkedQueue<AbstractStressorRecord>();
      private AtomicLong missingOperations = new AtomicLong();
      private volatile long lastStoredOperationTimestamp = Long.MIN_VALUE;

      public Pool(int numThreads, int numSlaves) {
         totalThreads = numThreads * numSlaves;
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
