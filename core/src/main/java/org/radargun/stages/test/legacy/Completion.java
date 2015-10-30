package org.radargun.stages.test.legacy;

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
    * Register code that should be called once this completion has first returned false from {@link #moreToRun()}.
    * @param runnable
    */
   void setCompletionHandler(Runnable runnable);

   /**
    * Optionally writes progress message to the log.
    * @param executedOps Number of operations executed by this stressor thread.
    */
   void logProgress(int executedOps);
}
