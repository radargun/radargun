package org.radargun.stages.test;

/**
 * Implementations specify what operations should be executed during the stress test.
 * Each stressor thread uses single instance of this class.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class OperationLogic {
   protected Stressor stressor;

   /**
    * Initialize this logic. No {@link org.radargun.Operation operations}
    * should be executed here.
    */
   public void init(Stressor stressor) {
      this.stressor = stressor;
   }

   /**
    * Execute operation on the stressor using its
    * {@link Stressor#makeRequest(Invocation)} makeRequest} method.
    * This operation accounts to the statistics.
    * Note: logic may actually execute more operations
    *
    * @return The method returns object in order to avoid JIT optimization by the compiler.
    */
   public abstract Object run() throws RequestException;

   /**
    * Handle started transaction - the logic should call {@link Stressor#wrap(Object)} on all resources
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
