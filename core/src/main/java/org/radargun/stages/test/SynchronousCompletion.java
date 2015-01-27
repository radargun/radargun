package org.radargun.stages.test;

import java.util.concurrent.Phaser;

/**
 * Synchronizes the request executions in order to start all the requests in parallel.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SynchronousCompletion implements Completion {
   private final Completion delegate;
   private final Phaser phaser;

   public SynchronousCompletion(Completion delegate, int threads) {
      this.delegate = delegate;
      this.phaser = new Phaser(threads);
   }

   @Override
   public boolean moreToRun(int opNumber) {
      int phase = phaser.arrive();
      if (phase < 0) return false;
      try {
         phase = phaser.awaitAdvanceInterruptibly(phase);
         if (phase < 0) {
            return false;
         }
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return false;
      }
      boolean more = delegate.moreToRun(opNumber);
      if (!more) {
         phaser.arriveAndDeregister();
      }
      return more;
   }

   @Override
   public void logProgress(int executedOps) {
      delegate.logProgress(executedOps);
   }
}
