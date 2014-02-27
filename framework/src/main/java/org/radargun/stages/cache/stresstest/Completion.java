package org.radargun.stages.cache.stresstest;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
public abstract class Completion {
   static final String PROGRESS_STRING = "Number of operations executed by this thread: %d. Elapsed time: $s. Remaining: %s. Total: %s.";
   protected static Log log = LogFactory.getLog(Completion.class);

   public abstract boolean moreToRun();
   public abstract void logProgress(int executedOps);
}
