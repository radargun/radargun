package org.radargun.stages.cache.background;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Debugable;
import org.radargun.utils.Utils;

/**
 * Log checkers control that all operations executed by stressors are persisted in the log values.
 * Each node checks all writes from all stressors, but there's not a one-to-one stressor-checker
 * relation. Instead, each node starts a set of of checker threads, which share a data structure
 * {@link StressorRecordPool} with records about each stressor,
 * represented by {@link org.radargun.stages.cache.background.StressorRecord} class.
 * All records are iterated through in a round-robin fashion by the checker threads.
 *
 * When the checkers are dead on particular node, this node cannot check the stressors. For some
 * scenarios this is limiting - therefore, stressors may be configured to unwind the log values
 * even if the old records are not checked. Then, it has to notify the checker about this action
 * via ignored_* key, to prevent it from failing the test.
 *
 * @see org.radargun.stages.cache.background.AbstractLogLogic
 * @see org.radargun.stages.cache.background.Stressor
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class LogChecker extends Thread {
   protected static final Log log = LogFactory.getLog(LogChecker.class);
   protected static final boolean trace = log.isTraceEnabled();
   protected static final long UNSUCCESSFUL_CHECK_MIN_DELAY_MS = 10;
   protected static final String LAST_OPERATION_PREFIX = "stressor_";
   protected static final ThreadLocal<DateFormat> formatter = new ThreadLocal<DateFormat>() {
      @Override
      protected DateFormat initialValue() {
         return new SimpleDateFormat("HH:mm:ss,S");
      }
   };
   protected final KeyGenerator keyGenerator;
   protected final int slaveIndex;
   protected final LogLogicConfiguration logLogicConfiguration;
   protected final StressorRecordPool stressorRecordPool;
   protected final FailureManager failureManager;
   protected final BasicOperations.Cache basicCache;
   protected final Debugable.Cache debugableCache;
   protected volatile boolean terminate = false;

   public LogChecker(String name, BackgroundOpsManager manager) {
      super(name);
      this.keyGenerator = manager.getKeyGenerator();
      this.slaveIndex = manager.getSlaveState().getIndexInGroup();
      this.logLogicConfiguration = manager.getLogLogicConfiguration();
      this.stressorRecordPool = manager.getStressorRecordPool();
      this.failureManager = manager.getFailureManager();
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
         StressorRecord record = null;
         try {
            if (delayedKeys > stressorRecordPool.getTotalThreads()) {
               Thread.sleep(UNSUCCESSFUL_CHECK_MIN_DELAY_MS);
            }
            record = stressorRecordPool.take();
            log.trace("Checking record: " + record.getStatus());
            if (System.currentTimeMillis() < record.getLastUnsuccessfulCheckTimestamp() + UNSUCCESSFUL_CHECK_MIN_DELAY_MS) {
               log.trace("Last unsuccessful check was performed too recently, delaying");
               delayedKeys++;
               continue;
            }
            delayedKeys = 0;
            if (record.getLastUnsuccessfulCheckTimestamp() > Long.MIN_VALUE) {
               // the last check was unsuccessful -> grab lastOperation BEFORE the value to check if we've lost that
               Object last = basicCache.get(lastOperationKey(record.getThreadId()));
               if (last != null) {
                  LastOperation lastOperation = (LastOperation) last;
                  record.addConfirmation(lastOperation.getOperationId(), lastOperation.getTimestamp());
               }
            }
            if (record.getOperationId() == 0) {
               Object last = basicCache.get(checkerKey(slaveIndex, record.getThreadId()));
               if (last != null) {
                  LastOperation lastCheck = (LastOperation) last;
                  record = newRecord(record, lastCheck.getOperationId(), lastCheck.getSeed());
               }
               checkIgnoreRecord(record);
               if (record.getOperationId() != 0) {
                  log.tracef("Check for thread %d continues from operation %d",
                             record.getThreadId(), record.getOperationId());
               }
            }
            if (trace) {
               log.tracef("Checking operation %d for thread %d on key %d (%s)",
                          record.getOperationId(), record.getThreadId(), record.getKeyId(), keyGenerator.generateKey(record.getKeyId()));
            }
            boolean notification = record.hasNotification(record.getOperationId());
            Object value = findValue(record);
            boolean contains = containsOperation(value, record);
            if (notification && contains) {
               if (trace) {
                  log.tracef("Found operation %d for thread %d", record.getOperationId(), record.getThreadId());
               }
               if (record.getOperationId() % logLogicConfiguration.getCounterUpdatePeriod() == 0) {
                  basicCache.put(checkerKey(slaveIndex, record.getThreadId()),
                        new LastOperation(record.getOperationId(), Utils.getRandomSeed(record.getRand())));
               }
               record.next();
               record.setLastUnsuccessfulCheckTimestamp(Long.MIN_VALUE);
               record.setLastSuccessfulCheckTimestamp(System.currentTimeMillis());
            } else {
               long confirmationTimestamp = record.getCurrentConfirmationTimestamp();
               if (confirmationTimestamp >= 0) {
                  log.debug("Detected stale read, keyId: " + keyGenerator.generateKey(record.getKeyId()));
               }
               if (confirmationTimestamp >= 0
                     && (logLogicConfiguration.writeApplyMaxDelay <= 0 || System.currentTimeMillis() > confirmationTimestamp + logLogicConfiguration.writeApplyMaxDelay)) {
                  // Verify whether record should not be ignored
                  if (checkIgnoreRecord(record)) {
                     continue;
                  }
                  if (!notification) {
                     log.errorf("Missing notification for operation %d for thread %d on key %d (%s), required for %d, notified for %s",
                                record.getOperationId(), record.getThreadId(), record.getKeyId(),
                           keyGenerator.generateKey(record.getKeyId()), record.getRequireNotify(), record.getNotifiedOps());
                     failureManager.reportMissingNotification();
                     debugFailure(record);
                  }
                  if (!contains) {
                     log.errorf("Missing operation %d for thread %d on key %d (%s) %s",
                                record.getOperationId(), record.getThreadId(), record.getKeyId(),
                           keyGenerator.generateKey(record.getKeyId()),
                           value == null ? " - entry was completely lost" : "");
                     log.errorf("Not found in %s", value);
                     failureManager.reportMissingOperation();
                     debugFailure(record);
                  }
                  record.next();
               } else {
                  long lastUnsuccessfulCheckTimestamp = System.currentTimeMillis();
                  log.debugf("Check of record %s unsuccessful, setting timestamp to %d", record.getStatus(), lastUnsuccessfulCheckTimestamp);
                  record.setLastUnsuccessfulCheckTimestamp(lastUnsuccessfulCheckTimestamp);
               }
            }
         } catch (Exception e) {
            log.error("Cannot check value for key " + keyGenerator.generateKey(record.getKeyId()), e);
         } finally {
            if (record == null) {
               try {
                  Thread.sleep(100);
               } catch (InterruptedException e) {
                  Thread.interrupted();
               }
            } else {
               stressorRecordPool.add(record);
            }
         }
      }
   }

   private boolean checkIgnoreRecord(StressorRecord record) {
      if (logLogicConfiguration.ignoreDeadCheckers) {
         Object ignored = basicCache.get(ignoredKey(slaveIndex, record.getThreadId()));
         if (ignored != null && record.getOperationId() <= (Long) ignored) {
            log.tracef("Operations %d - %d for thread %d are ignored", record.getOperationId(), ignored, record.getThreadId());
            while (record.getOperationId() <= (Long) ignored) {
               record.next();
            }
            return true;
         }
      }
      return false;
   }

   private void debugFailure(StressorRecord record) {
      if (logLogicConfiguration.isDebugFailures() && debugableCache != null) {
         debugableCache.debugInfo();
         debugableCache.debugKey(keyGenerator.generateKey(record.getKeyId()));
         debugableCache.debugKey(keyGenerator.generateKey(~record.getKeyId()));
      }
   }

   protected abstract StressorRecord newRecord(StressorRecord record, long operationId, long seed);

   protected abstract Object findValue(StressorRecord record) throws Exception;

   protected abstract boolean containsOperation(Object value, StressorRecord record);

   public static class LastOperation implements Serializable {
      private final long operationId;
      private final long seed;
      private final long timestamp;

      public LastOperation(long operationId, long seed) {
         this.operationId = operationId;
         this.seed = seed;
         this.timestamp = System.currentTimeMillis();
      }

      public long getOperationId() {
         return operationId;
      }

      public long getSeed() {
         return seed;
      }

      public long getTimestamp() {
         return timestamp;
      }

      @Override
      public String toString() {
         return String.format("LastOperation{operationId=%d, seed=%016X, timestamp=%s}", operationId, seed,
               formatter.get().format(new Date(timestamp)));
      }
   }

}
