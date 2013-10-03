package org.radargun.stressors;

import java.util.List;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.features.AtomicOperationsCapable;
import org.radargun.stages.helpers.Range;
import org.radargun.state.SlaveState;
import org.radargun.utils.Utils;

/**
* Stressor thread running during many stages.
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
* @since 1/3/13
*/
class BackgroundStressor extends Thread {

   private static final Log log = LogFactory.getLog(BackgroundStressor.class);
   private static final boolean trace = log.isTraceEnabled();

   private final int keyRangeStart;
   private final int keyRangeEnd;
   private final List<Range> deadSlavesRanges;
   private final BackgroundOpsManager manager;
   private final KeyGenerator keyGenerator;

   private final SynchronizedStatistics threadStats = new SynchronizedStatistics();
   private volatile boolean terminate = false;
   private int remainingTxOps;
   private boolean loaded;
   private final BackgroundStressor.Logic logic;
   private final int threadId;

   public BackgroundStressor(BackgroundOpsManager manager, SlaveState slaveState, Range myRange, List<Range> deadSlavesRanges, int slaveIndex, int idx) {
      super("StressorThread-" + idx);
      this.threadId = slaveIndex * manager.getNumThreads() + idx;
      this.manager = manager;
      this.keyRangeStart = myRange.getStart();
      this.keyRangeEnd = myRange.getEnd();
      this.deadSlavesRanges = deadSlavesRanges;
      this.keyGenerator = manager.getKeyGenerator();
      if (manager.isUseLogValues()) {
         if (manager.isSharedKeys()) {
            logic = new SharedLogLogic(threadId);
         } else {
            logic = new PrivateLogLogic(threadId);
         }
      } else {
         logic = new LegacyLogic(myRange.getStart());
      }
      this.remainingTxOps = manager.getTransactionSize();
   }

   private void loadData() {
      log.trace("Loading key range [" + keyRangeStart + ", " + keyRangeEnd + "]");
      loadKeyRange(keyRangeStart, keyRangeEnd);
      if (deadSlavesRanges != null) {
         for (Range range : deadSlavesRanges) {
            log.trace("Loading key range for dead slave: [" + range.getStart() + ", " + range.getEnd() + "]");
            loadKeyRange(range.getStart(), range.getEnd());
         }
      }
      loaded = true;
   }

   private void loadKeyRange(int from, int to) {
      int loaded_keys = 0;
      CacheWrapper cacheWrapper = manager.getCacheWrapper();
      boolean loadWithPutIfAbsent = manager.getLoadWithPutIfAbsent();
      AtomicOperationsCapable atomicWrapper = null;
      if (loadWithPutIfAbsent && !(cacheWrapper instanceof AtomicOperationsCapable)) {
         throw new IllegalArgumentException("This cache wrapper does not support atomic operations");
      } else {
         atomicWrapper = (AtomicOperationsCapable) cacheWrapper;
      }
      int entrySize = manager.getEntrySize();
      Random rand = new Random();
      for (long keyId = from; keyId < to && !terminate; keyId++, loaded_keys++) {
         while (!terminate) {
            try {
               Object key = keyGenerator.generateKey(keyId);
               if (loadWithPutIfAbsent) {
                  atomicWrapper.putIfAbsent(manager.getBucketId(), key, generateRandomEntry(rand, entrySize));
               } else {
                  cacheWrapper.put(manager.getBucketId(), key, generateRandomEntry(rand, entrySize));
               }
               if (loaded_keys % 1000 == 0) {
                  log.debug("Loaded " + loaded_keys + " out of " + (to - from));
               }
               // if we get an exception, it's OK - we can retry.
               break;
            } catch (Exception e) {
               log.error("Error while loading data", e);
            }
         }
      }
      log.debug("Loaded all " + (to - from) + " keys");
   }

   @Override
   public void run() {
      try {
         if (!loaded) {
            loadData();
         }
         if (manager.getLoadOnly()) {
            log.info("The stressor has finished loading data and will terminate.");
            return;
         }
         while (!isInterrupted() && !terminate) {
            logic.invoke();
            sleep(manager.getDelayBetweenRequests());
         }
      } catch (InterruptedException e) {
         log.trace("Stressor interrupted.");
         // we should close the transaction, otherwise TX Reaper would find dead thread in tx
         if (manager.getTransactionSize() != -1) {
            try {
               CacheWrapper cacheWrapper = manager.getCacheWrapper();
               if (cacheWrapper != null && cacheWrapper.isRunning()) {
                  cacheWrapper.endTransaction(false);
               }
            } catch (Exception e1) {
               log.error("Error while ending transaction", e);
            }
         }
      }
   }

   private InterruptedException findInterruptionCause(Throwable eParent, Throwable e) {
      if (e == null || eParent == e) {
         return null;
      } else if (e instanceof InterruptedException) {
         return (InterruptedException) e;
      } else {
         return findInterruptionCause(e, e.getCause());
      }
   }

   private byte[] generateRandomEntry(Random rand, int size) {
      // each char is 2 bytes
      byte[] data = new byte[size];
      rand.nextBytes(data);
      return data;
   }

   public boolean isLoaded() {
      return loaded;
   }

   public void setLoaded(boolean loaded) {
      this.loaded = loaded;
   }

   public void requestTerminate() {
      terminate = true;
   }

   public SynchronizedStatistics getStatsSnapshot(boolean reset, long time) {
      SynchronizedStatistics snapshot = threadStats.snapshot(reset,  time);
      return snapshot;
   }

   private interface Logic {
      public void invoke() throws InterruptedException;
   }

   private class LegacyLogic implements Logic {
      private final Random rand = new Random();
      private long currentKey;

      public LegacyLogic(long startKey) {
         currentKey = startKey;
      }

      public void invoke() throws InterruptedException {
         long startTime = 0;
         Object key = null;
         Operation operation = manager.getOperation(rand);
         CacheWrapper cacheWrapper = manager.getCacheWrapper();
         try {
            key = keyGenerator.generateKey(currentKey++);
            if (currentKey == keyRangeEnd) {
               currentKey = keyRangeStart;
            }
            int transactionSize = manager.getTransactionSize();
            if (transactionSize > 0 && remainingTxOps == transactionSize) {
               cacheWrapper.startTransaction();
            }
            startTime = System.nanoTime();
            Object result;
            switch (operation)
            {
            case GET:
               result = cacheWrapper.get(manager.getBucketId(), key);
               if (result == null) operation = Operation.GET_NULL;
               break;
            case PUT:
               cacheWrapper.put(manager.getBucketId(), key, generateRandomEntry(rand, manager.getEntrySize()));
               break;
            case REMOVE:
               cacheWrapper.remove(manager.getBucketId(), key);
               break;
            }
            threadStats.registerRequest(System.nanoTime() - startTime, 0, operation);
            if (transactionSize > 0) {
               remainingTxOps--;
               if (remainingTxOps == 0) {
                  cacheWrapper.endTransaction(true);
                  remainingTxOps = transactionSize;
               }
            }
         } catch (Exception e) {
            InterruptedException ie = findInterruptionCause(null, e);
            if (ie != null) {
               throw ie;
            } else if (e.getClass().getName().contains("SuspectException")) {
               log.error("Request failed due to SuspectException: " + e.getMessage());
            } else {
               log.error("Cache operation error", e);
            }
            if (manager.getTransactionSize() != -1) {
               try {
                  cacheWrapper.endTransaction(false);
               } catch (Exception e1) {
                  log.error("Error while ending transaction", e);
               }
               remainingTxOps = manager.getTransactionSize();
            }
            threadStats.registerError(startTime <= 0 ? 0 : System.nanoTime() - startTime, 0, operation);
         }
      }
   }

   private abstract class AbstractLogLogic implements Logic {

      protected final Random keySelectorRandom;
      protected final Random operationTypeRandom = new Random();
      protected final CacheWrapper cacheWrapper;
      protected long operationId = 0;
      private long txStartOperationId;
      private long txStartKeyId = -1;
      private long txStartRandSeed;
      private boolean txRolledBack = false;

      public AbstractLogLogic(long seed) {
         cacheWrapper = manager.getCacheWrapper();
         Random rand = null;
         try {
            Object last = cacheWrapper.get(manager.getBucketId(), LogChecker.lastOperationKey(threadId));
            if (last != null) {
               operationId = ((PrivateLogChecker.LastOperation) last).getOperationId() + 1;
               rand = Utils.setRandomSeed(new Random(0), ((PrivateLogChecker.LastOperation) last).getSeed());
               log.debug("Restarting operations from operation " + operationId);
            }
         } catch (Exception e) {
            log.error("Failure getting last operation", e);
         }
         if (rand == null) {
            log.trace("Initializing random with " + seed);
            this.keySelectorRandom = new Random(seed);
         } else {
            this.keySelectorRandom = rand;
         }
      }

      @Override
      public void invoke() throws InterruptedException {
         long keyId = nextKeyId();
         do {
            if (txRolledBack) {
               keyId = txStartKeyId;
               operationId = txStartOperationId;
               Utils.setRandomSeed(keySelectorRandom, txStartRandSeed);
               txRolledBack = false;
            }
            if (trace) {
               log.trace("Operation " + operationId + " on key " + keyId);
            }
         } while (!invokeOn(keyId) && !isInterrupted() && !terminate);
         operationId++;
      }

      protected abstract long nextKeyId();

      protected boolean invokeOn(long keyId) throws InterruptedException {
         try {
            int transactionSize = manager.getTransactionSize();
            if (transactionSize != -1 && remainingTxOps == transactionSize) {
               txStartOperationId = operationId;
               txStartKeyId = keyId;
               // we could serialize & deserialize instead, but that's not much better
               txStartRandSeed = Utils.getRandomSeed(keySelectorRandom);
               cacheWrapper.startTransaction();
            }

            if (!invokeLogic(keyId)) return false;

            // for non-transactional caches write the stressor last operation anytime (once in a while)
            if (transactionSize < 0 && operationId % manager.getLogCounterUpdatePeriod() == 0) {
               writeStressorLastOperation();
            }

            if (transactionSize != -1) {
               remainingTxOps--;
               if (remainingTxOps == 0) {
                  try {
                     cacheWrapper.endTransaction(true);
                  } catch (Exception e) {
                     log.trace("Transaction was rolled back, restarting from operation " + txStartOperationId);
                     txRolledBack = true;
                     return false;
                  } finally {
                     remainingTxOps = transactionSize;
                  }
                  // for non-transactional caches write the stressor last operation only after the transaction
                  // has finished
                  try {
                     cacheWrapper.startTransaction();
                     writeStressorLastOperation();
                     cacheWrapper.endTransaction(true);
                  } catch (Exception e) {
                     log.error("Cannot write stressor last operation", e);
                  }
               }
            }
            return true;
         } catch (Exception e) {
            InterruptedException ie = findInterruptionCause(null, e);
            if (ie != null) {
               throw ie;
            } else if (e.getClass().getName().contains("SuspectException")) {
               log.error("Request failed due to SuspectException: " + e.getMessage());
            } else {
               log.error("Cache operation error", e);
            }
            if (manager.getTransactionSize() > 0) {
               try {
                  cacheWrapper.endTransaction(false);
               } catch (Exception e1) {
                  log.error("Error while ending transaction, restarting from operation " + txStartOperationId, e);
                  txRolledBack = true;
               }
               remainingTxOps = manager.getTransactionSize();
            }
            return false; // on the same key
         }
      }

      private void writeStressorLastOperation() {
         try {
            // we have to write down the keySelectorRandom as well in order to be able to continue work if this slave
            // is restarted
            cacheWrapper.put(manager.getBucketId(), LogChecker.lastOperationKey(threadId),
                  new PrivateLogChecker.LastOperation(operationId, Utils.getRandomSeed(keySelectorRandom)));
         } catch (Exception e) {
            log.error("Error writing stressor last operation", e);
         }
      }

      protected abstract boolean invokeLogic(long keyId) throws Exception;

      protected long getLastCheckedOperation() {
         long minReadOperationId = Long.MAX_VALUE;
         for (int i = 0; i < manager.getNumSlaves(); ++i) {
            Object lastCheck = null;
            try {
               lastCheck = cacheWrapper.get(manager.getBucketId(), LogChecker.checkerKey(i, threadId));
            } catch (Exception e) {
               log.error("Cannot read last checked operation id for slave " + i + " and thread " + threadId, e);
            }
            long readOperationId = lastCheck == null ? Long.MIN_VALUE : ((LogChecker.LastOperation) lastCheck).getOperationId();
            minReadOperationId = Math.min(minReadOperationId, readOperationId);
         }
         return minReadOperationId;
      }
   }

   private class PrivateLogLogic extends AbstractLogLogic {

      private PrivateLogLogic(long seed) {
         super(seed);
      }

      @Override
      protected long nextKeyId() {
         return keySelectorRandom.nextInt(keyRangeEnd - keyRangeStart) + keyRangeStart;
      }

      @Override
      protected boolean invokeLogic(long keyId) throws Exception {
         Operation operation = manager.getOperation(operationTypeRandom);
         String bucketId = manager.getBucketId();

         // first we have to get the value
         PrivateLogValue prevValue = checkedGetValue(cacheWrapper, bucketId, keyId);
         // now for modify operations, execute it
         if (prevValue == null || operation == Operation.PUT) {
            PrivateLogValue nextValue;
            PrivateLogValue backupValue = null;
            if (prevValue != null) {
               nextValue = getNextValue(cacheWrapper, prevValue);
            } else {
               // the value may have been removed, look for backup
                backupValue = checkedGetValue(cacheWrapper, bucketId, ~keyId);
               if (backupValue == null) {
                  nextValue = new PrivateLogValue(threadId, operationId);
               } else {
                  nextValue = getNextValue(cacheWrapper, backupValue);
               }
            }
            if (nextValue == null) {
               return false;
            }
            checkedPutValue(cacheWrapper, bucketId, keyId, nextValue);
            if (backupValue != null) {
               checkedRemoveValue(cacheWrapper, bucketId, ~keyId, backupValue);
            }
         } else if (operation == Operation.REMOVE) {
            PrivateLogValue nextValue = getNextValue(cacheWrapper, prevValue);
            if (nextValue == null) {
               return false;
            }
            checkedPutValue(cacheWrapper, bucketId, ~keyId, nextValue);
            checkedRemoveValue(cacheWrapper, bucketId, keyId, prevValue);
         } else {
            // especially GETs are not allowed here, because these would break the deterministic order
            // - each operationId must be written somewhere
            throw new UnsupportedOperationException("Only PUT and REMOVE operations are allowed for this logic.");
         }
         return true;
      }

      private PrivateLogValue getNextValue(CacheWrapper cacheWrapper, PrivateLogValue prevValue) throws InterruptedException {
         if (prevValue.size() >= manager.getLogValueMaxSize()) {
            int checkedValues;
            // TODO some limit after which the stressor will terminate
            for (;;) {
               if (isInterrupted() || terminate) {
                  return null;
               }
               long minReadOperationId = getLastCheckedOperation();
               if (prevValue.getOperationId(0) <= minReadOperationId) {
                  for (checkedValues = 1; checkedValues < prevValue.size() && prevValue.getOperationId(checkedValues) <= minReadOperationId; ++checkedValues);
                  break;
               } else {
                  try {
                     Thread.sleep(100);
                  } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                     return null;
                  }
               }
            }
            return prevValue.shift(checkedValues, operationId);
         } else {
            return prevValue.with(operationId);
         }
      }

      private PrivateLogValue checkedGetValue(CacheWrapper cacheWrapper, String bucketId, long keyId) throws Exception {
         Object prevValue;
         long startTime = System.nanoTime();
         try {
            prevValue = cacheWrapper.get(bucketId, keyGenerator.generateKey(keyId));
         } catch (Exception e) {
            threadStats.registerError(System.nanoTime() - startTime, 0, Operation.GET);
            throw e;
         }
         long endTime = System.nanoTime();
         if (prevValue != null && !(prevValue instanceof PrivateLogValue)) {
            threadStats.registerError(endTime - startTime, 0, Operation.GET);
            log.error("Value is not an instance of PrivateLogValue: " + prevValue);
            throw new IllegalStateException();
         } else {
            threadStats.registerRequest(endTime - startTime, 0, prevValue == null ? Operation.GET_NULL : Operation.GET);
            return (PrivateLogValue) prevValue;
         }
      }

      private PrivateLogValue checkedRemoveValue(CacheWrapper cacheWrapper, String bucketId, long keyId, PrivateLogValue expectedValue) throws Exception {
         Object prevValue;
         long startTime = System.nanoTime();
         try {
            prevValue = cacheWrapper.remove(bucketId, keyGenerator.generateKey(keyId));
         } catch (Exception e) {
            threadStats.registerError(System.nanoTime() - startTime, 0, Operation.REMOVE);
            throw e;
         }
         long endTime = System.nanoTime();
         boolean successful = false;
         if (prevValue != null) {
            if (!(prevValue instanceof PrivateLogValue)) {
               log.error("Value is not an instance of PrivateLogValue: " + prevValue);
            } else if (!prevValue.equals(expectedValue)) {
               log.error("Value is not the expected one: expected=" + expectedValue + ", found=" + prevValue);
            } else {
               successful = true;
            }
         } else if (expectedValue == null) {
            successful = true;
         } else {
            log.error("Expected to remove null-value but found " + prevValue);
         }
         if (successful) {
            threadStats.registerRequest(endTime - startTime, 0, Operation.REMOVE);
            return (PrivateLogValue) prevValue;
         } else {
            threadStats.registerError(endTime - startTime, 0, Operation.REMOVE);
            throw new IllegalStateException();
         }
      }

      private void checkedPutValue(CacheWrapper cacheWrapper, String bucketId, long keyId, PrivateLogValue value) throws Exception {
         long startTime = System.nanoTime();
         try {
            cacheWrapper.put(bucketId, keyGenerator.generateKey(keyId), value);
         } catch (Exception e) {
            threadStats.registerError(System.nanoTime() - startTime, 0, Operation.PUT);
            throw e;
         }
         long endTime = System.nanoTime();
         threadStats.registerRequest(endTime - startTime, 0, Operation.PUT);
      }
   }

   private class SharedLogLogic extends AbstractLogLogic {
      private CacheWrapper cacheWrapper;
      private AtomicOperationsCapable atomicWrapper;

      public SharedLogLogic(long seed) {
         super(seed);
         cacheWrapper = manager.getCacheWrapper();
         atomicWrapper = (AtomicOperationsCapable) cacheWrapper;
      }

      @Override
      protected long nextKeyId() {
         return keySelectorRandom.nextInt(manager.getNumEntries());
      }

      @Override
      protected boolean invokeLogic(long keyId) throws Exception {
         Operation operation = manager.getOperation(operationTypeRandom);
         String bucketId = manager.getBucketId();

         // In shared mode, we can't ever atomically modify the two keys (main and backup) to have only
         // one of them with the actual value (this is not true even for private mode but there the moment
         // when we move the value from main to backup or vice versa does not cause any problem, because the
         // worst thing is to read slightly outdated value). However, here the invariant holds that the operation
         // must be recorded in at least one of the entries, but the situation with both of these having
         // some value is valid (although, we try to evade it be conditionally removing one of them in each
         // logic step).
         SharedLogValue prevValue = checkedGetValue(cacheWrapper, bucketId, keyId);
         SharedLogValue backupValue = checkedGetValue(cacheWrapper, bucketId, ~keyId);
         SharedLogValue nextValue = getNextValue(atomicWrapper, prevValue, backupValue);
         // now for modify operations, execute it
         if (operation == Operation.PUT) {
            if (checkedPutValue(atomicWrapper, bucketId, keyId, prevValue, nextValue)) {
               if (backupValue != null) {
                  checkedRemoveValue(atomicWrapper, bucketId, ~keyId, backupValue);
               }
            } else {
               return false;
            }
         } else if (operation == Operation.REMOVE) {
            if (checkedPutValue(atomicWrapper, bucketId, ~keyId, backupValue, nextValue)) {
               if (prevValue != null) {
                  checkedRemoveValue(atomicWrapper, bucketId, keyId, prevValue);
               }
            } else {
               return false;
            }
         } else {
            // especially GETs are not allowed here, because these would break the deterministic order
            // - each operationId must be written somewhere
            throw new UnsupportedOperationException("Only PUT and REMOVE operations are allowed for this logic.");
         }
         return true;
      }

      private SharedLogValue getNextValue(AtomicOperationsCapable cacheWrapper, SharedLogValue prevValue, SharedLogValue backupValue) {
         if (prevValue == null && backupValue == null) {
            return new SharedLogValue(threadId, operationId);
         } else if (prevValue != null && backupValue != null) {
            SharedLogValue joinValue = prevValue.join(backupValue);
            if (joinValue.size() >= manager.getLogValueMaxSize()) {
               return filterAndAddOperation(joinValue);
            } else {
               return joinValue.with(threadId, operationId);
            }
         }
         SharedLogValue value = prevValue != null ? prevValue : backupValue;
         if (value.size() < manager.getLogValueMaxSize()) {
            return value.with(threadId, operationId);
         } else {
            return filterAndAddOperation(value);
         }
      }

      private SharedLogValue filterAndAddOperation(SharedLogValue value) {
         // TODO some limit after which the stressor will terminate
         while (!terminate && !isInterrupted()) {
            long minReadOperationId = getLastCheckedOperation();
            SharedLogValue filtered = value.with(threadId, operationId, minReadOperationId);
            if (filtered.size() > manager.getLogValueMaxSize()) {
               try {
                  Thread.sleep(100);
               } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return null;
               }
            } else {
               return filtered;
            }
         }
         return null;
      }

      private SharedLogValue checkedGetValue(CacheWrapper cacheWrapper, String bucketId, long keyId) throws Exception {
         Object prevValue;
         long startTime = System.nanoTime();
         try {
            prevValue = cacheWrapper.get(bucketId, keyGenerator.generateKey(keyId));
         } catch (Exception e) {
            threadStats.registerError(System.nanoTime() - startTime, 0, Operation.GET);
            throw e;
         }
         long endTime = System.nanoTime();
         if (prevValue != null && !(prevValue instanceof SharedLogValue)) {
            threadStats.registerError(endTime - startTime, 0, Operation.GET);
            log.error("Value is not an instance of SharedLogValue: " + prevValue);
            throw new IllegalStateException();
         } else {
            threadStats.registerRequest(endTime - startTime, 0, prevValue == null ? Operation.GET_NULL : Operation.GET);
            return (SharedLogValue) prevValue;
         }
      }

      private boolean checkedPutValue(AtomicOperationsCapable cacheWrapper, String bucketId, long keyId, SharedLogValue oldValue, SharedLogValue newValue) throws Exception {
         boolean returnValue;
         long startTime = System.nanoTime();
         try {
            if (oldValue == null) {
               returnValue = cacheWrapper.putIfAbsent(bucketId, keyGenerator.generateKey(keyId), newValue) == null;
            } else {
               returnValue = cacheWrapper.replace(bucketId, keyGenerator.generateKey(keyId), oldValue, newValue);
            }
         } catch (Exception e) {
            threadStats.registerError(System.nanoTime() - startTime, 0, Operation.PUT);
            throw e;
         }
         long endTime = System.nanoTime();
         threadStats.registerRequest(endTime - startTime, 0, Operation.PUT);
         return returnValue;
      }

      private boolean checkedRemoveValue(AtomicOperationsCapable cacheWrapper, String bucketId, long keyId, SharedLogValue oldValue) throws Exception {
         long startTime = System.nanoTime();
         try {
            boolean returnValue = cacheWrapper.remove(bucketId, keyGenerator.generateKey(keyId), oldValue);
            long endTime = System.nanoTime();
            threadStats.registerRequest(endTime - startTime, 0, Operation.REMOVE);
            return returnValue;
         } catch (Exception e) {
            threadStats.registerError(System.nanoTime() - startTime, 0, Operation.REMOVE);
            throw e;
         }
      }
   }
}
