package org.radargun.stages.cache.background;

/**
 * Checker used for {@link org.radargun.stages.cache.background.PrivateLogValue shared log values}
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SharedLogChecker extends LogChecker {

   public SharedLogChecker(int id, BackgroundOpsManager manager) {
      super(manager.getName() + "-Checker-" + id, manager);
   }

   @Override
   protected StressorRecord newRecord(StressorRecord record, long operationId, long seed) {
      return new StressorRecord(record, operationId, seed);
   }

   @Override
   protected Object findValue(StressorRecord record) throws Exception {
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
         if (keyId < 0 && record.getCurrentConfirmationTimestamp() < 0) {
            // do not poll it 100x when we're not sure that the operation is written, try just twice
            break;
         }
         keyId = ~keyId;
      }
      return value;
   }

   @Override
   protected boolean containsOperation(Object value, StressorRecord record) {
      if (value == null) {
         return false;
      }
      if (!(value instanceof SharedLogValue)) {
         log.error("Key " + record.getKeyId() + " has unexpected value " + value);
         return false;
      }
      SharedLogValue logValue = (SharedLogValue) value;
      if (logValue.contains(record.getThreadId(), record.getOperationId())) {
         return true;
      }
      return false;
   }

}
