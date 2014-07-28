package org.radargun.stages.cache.stresstest;

/**
 * Implementations specify what operations should be executed during the stress test.
 * Each stressor thread uses single instance of this class.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
interface OperationLogic {
   /**
    * Initialize this logic, possibly executing some operations prior to the test itself.
    * However, these operations are not benchmarked.
    */
   void init(Stressor stressor);

   /**
    * Execute one operation on the stressor using its
    * {@link Stressor#makeRequest(org.radargun.Operation, Object...)} makeRequest} method.
    * This operation accounts to the statistics.
    */
   Object run(Stressor stressor) throws RequestException;

   static class RequestException extends Exception {
      public RequestException(Throwable cause) {
         super(cause);
      }
   }
}
