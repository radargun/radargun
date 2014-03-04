package org.radargun.stages.cache.stresstest;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
interface OperationLogic {
   void init(int threadIndex, int nodeIndex, int numNodes);
   Object run(Stressor stressor) throws RequestException;

   static class RequestException extends Exception {
      public RequestException(Throwable cause) {
         super(cause);
      }
   }
}
