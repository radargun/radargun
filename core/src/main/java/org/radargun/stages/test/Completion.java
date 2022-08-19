package org.radargun.stages.test;

/**
 * Limits the duration of test.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Completion {
   void start();

   /**
    * @return True if the stress test execution should continue
    */
   boolean moreToRun();

   /**
    * Optionally writes progress message to the log.
    * @param executedOps Number of operations executed by this stressor thread.
    */
   void logProgress(int executedOps);
}
