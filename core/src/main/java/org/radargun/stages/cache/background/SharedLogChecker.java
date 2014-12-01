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

      public Pool(int numSlaves, int numThreads, long numEntries, long keyIdOffset, BackgroundOpsManager manager) {
         super(numThreads, numSlaves, manager);
         int totalThreads = numThreads * numSlaves;
         for (int threadId = 0; threadId < totalThreads; ++threadId) {
            log.tracef("Record %d has range 0 - %d", threadId, numEntries);
            addNew(new StressorRecord(threadId, numEntries, keyIdOffset));
         }
         registerListeners(true); // synchronous listeners
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
         if (value instanceof SharedLogValue) {
            SharedLogValue logValue = (SharedLogValue) value;
            int last = logValue.size() - 1;
            notify(logValue.getThreadId(last), logValue.getOperationId(last), key);
         } else super.modified(key, value);
      }
   }


   private static class StressorRecord extends AbstractStressorRecord {
      private final long numEntries;
      private final long keyIdOffset;

      public StressorRecord(int threadId, long numEntries, long keyIdOffset) {
         super(new Random(threadId), threadId);
         this.numEntries = numEntries;
         this.keyIdOffset = keyIdOffset;
         next();
      }

      public StressorRecord(SharedLogChecker.StressorRecord record, long operationId, long seed) {
         super(seed, record.getThreadId(), operationId);
         this.numEntries = record.numEntries;
         this.keyIdOffset = record.keyIdOffset;
         next();
      }

      @Override
      public void next() {
         currentKeyId = (rand.nextLong() & Long.MAX_VALUE) % numEntries + keyIdOffset;
         discardNotification(currentOp++);
      }
   }
}
