package org.radargun.stages.cache.background;

import java.util.Random;

import org.radargun.stages.helpers.Range;

/**
 * Checker used for {@link PrivateLogValue non-shared log values}
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class PrivateLogChecker extends LogChecker {

   public PrivateLogChecker(int id, Pool logCheckerPool, BackgroundOpsManager manager) {
      super("PrivateLogChecker-" + id, manager, logCheckerPool);
   }

   @Override
   protected AbstractStressorRecord newRecord(AbstractStressorRecord record, long operationId, long seed) {
      return new StressorRecord((StressorRecord) record, operationId, seed);
   }

   @Override
   protected Object findValue(AbstractStressorRecord record) throws Exception {
      // We cannot atomically get the two vars - the stressor could move backup to main or back between the two
      // gets. But the chance for doing this 100 times is small enough.
      Object value = null;
      long keyId = record.getKeyId();
      for (int i = 0; i < 100; ++i) {
         value = basicCache.get(keyGenerator.generateKey(keyId));
         if (value == null) {
            if (keyId < 0 && record.getLastStressorOperation() < record.getOperationId()) {
               // do not poll it 100x when we're not sure that the operation is written, try just twice
               break;
            }
            keyId = ~keyId;
            if (keyId > 0) {
               // we yield because of the first entry to empty cache
               Thread.yield();
            }
         } else {
            break;
         }
      }
      return value;
   }

   @Override
   protected boolean containsOperation(Object value, AbstractStressorRecord record) {
      if (value == null) {
         return false;
      }
      if (!(value instanceof PrivateLogValue)) {
         log.error("Key " + record.getKeyId() + " has unexpected value " + value);
         return false;
      }
      PrivateLogValue logValue = (PrivateLogValue) value;
      if (logValue.getThreadId() == record.getThreadId()) {
         for (int i = logValue.size() - 1; i >= 0; --i) {
            if (logValue.getOperationId(i) == record.getOperationId()) {
               return true;
            }
         }
      } else {
         log.error("Expected value from threadId " + record.getThreadId() + " but found from " + logValue.getThreadId());
      }
      return false;
   }

   public static class Pool extends LogChecker.Pool {

      public Pool(int numSlaves, int numThreads, int numEntries, BackgroundOpsManager manager) {
         super(numThreads, numSlaves, manager);
         for (int slaveId = 0; slaveId < numSlaves; ++slaveId) {
            Range slaveKeyRange = Range.divideRange(numEntries, numSlaves, slaveId);
            for (int threadId = 0; threadId < numThreads; ++threadId) {
               Range threadKeyRange = Range.divideRange(slaveKeyRange.getSize(), numThreads, threadId);
               Range range = threadKeyRange.shift(slaveKeyRange.getStart());
               log.trace("Expecting range " + range + " for thread " + (slaveId * numThreads + threadId));
               addNew(new StressorRecord(slaveId * numThreads + threadId, range));
            }
         }
         registerListeners();
      }

      @Override
      public void created(Object key, Object value) {
         log.trace("Created " + key + " -> " + value);
         modified(key, value);
      }

      @Override
      public void updated(Object key, Object value) {
         log.trace("Updated " + key + " -> " + value);
         modified(key, value);
      }

      @Override
      protected void modified(Object key, Object value) {
         if (value instanceof PrivateLogValue) {
            PrivateLogValue logValue = (PrivateLogValue) value;
            notify(logValue.getThreadId(), logValue.getOperationId(logValue.size() - 1), key);
         } else super.modified(key, value);
      }
   }

   private static class StressorRecord extends AbstractStressorRecord {
      private final long keyRangeStart;
      private final int keyRangeSize;

      public StressorRecord(int threadId, Range keyRange) {
         super(new Random(threadId), threadId);
         this.keyRangeStart = keyRange.getStart();
         this.keyRangeSize = keyRange.getSize();
         next();
      }

      public StressorRecord(StressorRecord record, long operationId, long seed) {
         super(seed, record.threadId, operationId);
         this.keyRangeStart = record.keyRangeStart;
         this.keyRangeSize = record.keyRangeSize;
         next();
      }

      @Override
      public void next() {
         currentKeyId = keyRangeStart + rand.nextInt(keyRangeSize);
         discardNotification(currentOp++);
      }
   }

}
