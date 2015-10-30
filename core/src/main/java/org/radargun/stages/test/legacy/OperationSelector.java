package org.radargun.stages.test.legacy;

import java.util.Random;

import org.radargun.Operation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface OperationSelector {
   /**
    * This method should be invoked by every {@link LegacyStressor} before the recorded phase
    */
   void start();

   /**
    * Potentionally blocks the calling thread, and then returns next operation that should be invoked.
    * @param random
    * @return Next operation - can be null (in that case it's only up to the {@link OperationLogic}.
    */
   Operation next(Random random);

   OperationSelector DUMMY = new OperationSelector() {
      @Override
      public void start() {
      }

      @Override
      public Operation next(Random random) {
         return null;
      }
   };
}
