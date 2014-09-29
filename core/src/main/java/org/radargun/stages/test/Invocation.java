package org.radargun.stages.test;

import org.radargun.Operation;

/**
 * Represent an operation that the {@link org.radargun.stages.test.Stressor}
 * should execute and record its duration.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Invocation {
   /**
    * Invoke the operation.
    */
   Object invoke();

   /**
    * Operation that was executed.
    * @return
    */
   Operation operation();

   /**
    * Operation variant if this was executed within transaction.
    * @return
    */
   Operation txOperation();
}
