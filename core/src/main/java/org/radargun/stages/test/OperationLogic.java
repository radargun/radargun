package org.radargun.stages.test;

import org.radargun.Operation;

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
    * Release resources held by this logic. To be overriden in inheritors.
    */
   public void destroy() {}

   /**
    * Execute operation on the stressor using its
    * {@link Stressor#makeRequest(Invocation)} makeRequest} method.
    * This operation accounts to the statistics.
    * Note: logic may actually execute more operations
    *
    * @return The method returns object in order to avoid JIT optimization by the compiler.
    * @param operation
    */
   public abstract void run(Operation operation) throws RequestException;

   public static class RequestException extends Exception {
      public RequestException(Throwable cause) {
         super(cause);
      }
   }
}
