package org.radargun.stages.cache.stresstest;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Limits the duration of stress test.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class Completion {
   static final String PROGRESS_STRING = "Number of operations executed by this thread: %d. Elapsed time: %s. Remaining: %s. Total: %s.";
   protected static Log log = LogFactory.getLog(Completion.class);

   /**
    * @return True if the stress test execution should continue
    */
   public abstract boolean moreToRun();

   /**
    * Optionally writes progress message to the log.
    * @param executedOps Number of operations executed by this stressor thread.
    */
   public abstract void logProgress(int executedOps);
}
