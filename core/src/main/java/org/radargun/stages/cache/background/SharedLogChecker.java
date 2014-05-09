package org.radargun.stages.cache.background;

import java.util.Random;

/**
 * Checker used for {@link PrivateLogValue shared log values}
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
class SharedLogChecker extends LogChecker {

   public SharedLogChecker(int id, Pool pool, BackgroundOpsManager manager) {
      super("SharedLogChecker-" + id, manager, pool);
   }

   @Override
   protected AbstractStressorRecord newRecord(AbstractStressorRecord record, long operationId, long seed) {
      return new StressorRecord((StressorRecord) record, operationId, seed);
   }

   @Override
   protected Object findValue(AbstractStressorRecord record) throws Exception {
      // The value can always be moved so that we can't read it directly. However, if the value has not changed
      // since previous read, the complement could not have been removed - the operation definitely is not
      // in any entry.
      Object value = null, prevValue = null, prev2Value;
      long keyId = record.getKeyId();
      for (int i = 0; i < 100; ++i) {
         prev2Value = prevValue;
         prevValue = value;
         value = basicCache.get(keyGenerator.generateKey(keyId));
         if (containsOperation(value, record) || (value != null && value.equals(prev2Value))) {
            break;
         }
         if (keyId < 0 && record.getLastStressorOperation() < record.getOperationId()) {
            // do not poll it 100x when we're not sure that the operation is written, try just twice
            break;
         }
         keyId = ~keyId;
      }
      return value;
   }

   @Override
   protected boolean containsOperation(Object value, AbstractStressorRecord record) {
      if (value == null) {
         return false;
      }
      if (!(value instanceof SharedLogValue)) {
         log.error("Key " + record.getKeyId() + " has unexpected value " + value);
         return false;
      }
      SharedLogValue logValue = (SharedLogValue) value;
      for (int i = logValue.size() - 1; i >= 0; --i) {
         if (logValue.getThreadId(i) != record.getThreadId()) {
            continue;
         }
         // we can't increase the last stressor operation from seeing next one - these could be committed
         // in different order than actually written
         if (logValue.getOperationId(i) == record.getOperationId()) {
            return true;
         }
      }
      return false;
   }

   public static class Pool extends LogChecker.Pool {

      public Pool(int numSlaves, int numThreads, int numEntries, BackgroundOpsManager manager) {
         super(numThreads, numSlaves, manager);
         for (int slaveId = 0; slaveId < numSlaves; ++slaveId) {
            for (int threadId = 0; threadId < numThreads; ++threadId) {
               add(new StressorRecord(slaveId * numThreads + threadId, numEntries));
            }
         }
      }

   }

   private static class StressorRecord extends AbstractStressorRecord {
      private final int numEntries;

      public StressorRecord(int threadId, int numEntries) {
         super(new Random(threadId), threadId);
         this.numEntries = numEntries;
         next();
      }

      public StressorRecord(SharedLogChecker.StressorRecord record, long operationId, long seed) {
         super(seed, record.getThreadId(), operationId);
         this.numEntries = record.numEntries;
         next();
      }

      @Override
      public void next() {
         currentKeyId = rand.nextInt(numEntries);
         currentOp++;
      }
   }
}
