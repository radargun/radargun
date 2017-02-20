package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.Blackhole;
import org.radargun.state.ServiceListener;

/**
 * @author Radim Vansa &ltrvansa@redhat.com&gt;
 */
@Stage(doc = "Burns CPU time in several threads to simulate CPU intensive app.")
public class CpuBurnStage extends AbstractDistStage {
   @Property(doc = "Number of threads burning CPU.")
   public int numThreads;

   @Property(doc = "If set to true, all threads are stopped and the num-threads attribute is ignored.")
   public boolean stop = false;

   @Override
   public DistStageAck executeOnSlave() {
      State state = (State) slaveState.remove(CpuBurnStage.class.getName());
      if (stop) {
         if (state != null) {
            state.stop();
         } else {
            log.warn("There are no running threads!");
         }
      } else {
         if (state != null) {
            log.warn("There are already running threads!");
            // stop the old threads even if stop = false, otherwise we would leak them
            state.stop();
         }
         if (numThreads <= 0) {
            return errorResponse("Cannot use num-threads <= 0!");
         }
         slaveState.put(CpuBurnStage.class.getName(), new State(numThreads));
      }
      return successfulResponse();
   }

   private class State implements ServiceListener{
      final Thread[] threads;
      volatile boolean terminate = false;

      private State(int numThreads) {
         threads = new Thread[numThreads];
         for (int i = 0; i < numThreads; ++i) {
            threads[i] = new Thread(() -> {
               while (!terminate) {
                  Blackhole.consumeCpu();
               }
            }, "CpuBurner-" + i);
            threads[i].start();
         }
         slaveState.addListener(this);
      }

      public void stop() {
         terminate = true;
         for (Thread t : threads) {
            try {
               t.join();
            } catch (InterruptedException e) {
               log.warn("Interrupted while waiting for thread to finish", e);
               Thread.currentThread().interrupt();
            }
         }
         slaveState.removeListener(this);
      }

      @Override
      public void beforeServiceStop(boolean graceful) {
         stop();
      }
   }
}
