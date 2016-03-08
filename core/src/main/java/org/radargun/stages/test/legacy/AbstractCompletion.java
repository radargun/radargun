package org.radargun.stages.test.legacy;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.TimeService;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractCompletion implements Completion {
   protected static final String PROGRESS_STRING = "Number of operations executed by this thread: %d. Elapsed time: %s. Remaining: %s. Total: %s.";
   protected static final Log log = LogFactory.getLog(Completion.class);

   protected boolean started, completed;
   protected long startTime;
   private Runnable handler;

   @Override
   public synchronized void start() {
      if (!started) {
         startTime = TimeService.nanoTime();
         started = true;
      }
   }

   @Override
   public synchronized void setCompletionHandler(Runnable handler) {
      if (completed) throw new IllegalStateException();
      this.handler = handler;
   }

   protected synchronized void runCompletionHandler() {
      if (completed) return;
      completed = true;
      if (handler != null) {
         handler.run();
      }
   }
}
