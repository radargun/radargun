package org.radargun.stages.test;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.TimeService;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractCompletion implements Completion {
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

   protected synchronized void runCompletionHandler() {
      if (completed) return;
      completed = true;
      if (handler != null) {
         handler.run();
      }
   }
}
