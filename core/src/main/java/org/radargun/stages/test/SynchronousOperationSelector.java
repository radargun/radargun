package org.radargun.stages.test;

import java.util.Random;
import java.util.concurrent.Phaser;

import org.radargun.Operation;

/**
 * Synchronizes the request executions in order to start all the requests in parallel.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SynchronousOperationSelector implements OperationSelector {
   private final OperationSelector delegate;
   private final Phaser phaser;

   public SynchronousOperationSelector(OperationSelector delegate) {
      this.delegate = delegate;
      this.phaser = new Phaser();
   }

   @Override
   public Operation next(Random random) {
      int phase = phaser.arrive();
      if (phase < 0) return null;
      try {
         phase = phaser.awaitAdvanceInterruptibly(phase);
         if (phase < 0) {
            return null;
         }
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return null;
      }
      return delegate.next(random);
   }

   @Override
   public void start() {
      phaser.register();
      delegate.start();
   }
}
