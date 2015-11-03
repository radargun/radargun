package org.radargun.stages.test;

/**
 * Set of operations executed synchronously.
 * Conversation should do a proper cleanup after it's executed.
 * It's up to conversation to define think-time between invocation
 * of operations.
 * Interrupts should be handled by throwing {@link InterruptedException}.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Conversation {
   /**
    * This method can be invoked from multiple threads in parallel,
    * and the execution on one should not block others.
    *
    * @param stressor
    * @throws InterruptedException
    */
   void run(Stressor stressor) throws InterruptedException;

}
