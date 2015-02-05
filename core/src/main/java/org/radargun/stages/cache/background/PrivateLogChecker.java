package org.radargun.stages.cache.background;

/**
 * Checker used for {@link org.radargun.stages.cache.background.PrivateLogValue non-shared log values}
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class PrivateLogChecker extends LogChecker {

   public PrivateLogChecker(int id, LogCheckerPool pool, BackgroundOpsManager manager) {
      super(manager.getName() + "-Checker-" + id, manager, pool);
   }

   @Override
   protected StressorRecord newRecord(StressorRecord record, long operationId, long seed) {
      return new StressorRecord( record, operationId, seed);
   }

   @Override
   protected Object findValue(StressorRecord record) throws Exception {
      // We cannot atomically get the two vars - the stressor could move backup to main or back between the two
      // gets. But the chance for doing this 100 times is small enough.
      Object value = null;
      long keyId = record.getKeyId();
      for (int i = 0; i < 100; ++i) {
         value = basicCache.get(keyGenerator.generateKey(keyId));
         if (value == null) {
            if (keyId < 0 && record.getCurrentConfirmationTimestamp() < 0) {
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
   protected boolean containsOperation(Object value, StressorRecord record) {
      if (value == null) {
         return false;
      }
      if (!(value instanceof PrivateLogValue)) {
         log.error("Key " + record.getKeyId() + " has unexpected value " + value);
         return false;
      }
      PrivateLogValue logValue = (PrivateLogValue) value;
      if (logValue.getThreadId() == record.getThreadId()) {
         if (logValue.contains(record.getOperationId())) {
            return true;
         }
      } else {
         log.error("Expected value from threadId " + record.getThreadId() + " but found from " + logValue.getThreadId());
      }
      return false;
   }

}
