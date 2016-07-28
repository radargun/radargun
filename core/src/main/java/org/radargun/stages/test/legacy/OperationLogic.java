package org.radargun.stages.test.legacy;

import org.radargun.Operation;

/**
 * Implementations specify what operations should be executed during the stress test.
 * Each stressor thread uses single instance of this class.
 */
public abstract class OperationLogic {
   protected LegacyStressor stressor;

   /**
    * Initialize this logic. No {@link org.radargun.Operation operations}
    * should be executed here.
    */
   public void init(LegacyStressor stressor) {
      this.stressor = stressor;
   }

   /**
    * Release resources held by this logic. To be overriden in inheritors.
    */
   public void destroy() {}

   /**
    * Execute operation on the stressor using its
    * {@link LegacyStressor#makeRequest(Invocation)} makeRequest} method.
    * This operation accounts to the statistics.
    * Note: logic may actually execute more operations
    *
    * @return The method returns object in order to avoid JIT optimization by the compiler.
    * @param operation
    */
   public abstract void run(Operation operation) throws RequestException;

   /**
    * Handle started transaction - the logic should call {@link LegacyStressor#wrap(Object)} on all resources
    * used in the further invocation
    */
   public void transactionStarted() {
      throw new UnsupportedOperationException("Transactions are not supported.");
   }

   /**
    * Handle finished transaction - drop all wrapped resources
    */
   public void transactionEnded() {
      throw new UnsupportedOperationException("Transactions are not supported.");
   }

   public static class RequestException extends Exception {
      public RequestException(Throwable cause) {
         super(cause);
      }
   }
}
