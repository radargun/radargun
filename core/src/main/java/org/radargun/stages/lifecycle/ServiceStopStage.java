package org.radargun.stages.lifecycle;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.utils.TimeConverter;
import org.radargun.stages.AbstractDistStage;

/**
 * 
 * Will simulate a node stop on specified nodes. If the used Service does not provide Killable trait
 * it will always stop the node gracefully.
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Stops or kills (simulates node crash) one or more nodes.")
public class ServiceStopStage extends AbstractDistStage {

   private static final String STOP_DELAY_THREAD = "_STOP_DELAY_THREAD_";

   @Property(doc = "If set to false, the node crash should be simulated. By default node should be shutdown gracefully.")
   private boolean graceful = true;

   @Property(doc = "If set to true the benchmark will not wait until the node is stopped. Default is false.")
   private boolean async = false;

   @Property(converter = TimeConverter.class, doc = "If this value is positive the stage will spawn a thread which " +
         "will stop the node after the delay. The stage will not wait for anything. By default the stop is immediate " +
         "and synchronous.")
   private long delayExecution;

   @Property(doc="If set, the stage will not stop any node but will wait until the delayed execution is finished. " +
         "Default is false.")
   private boolean waitForDelayed = false;

   public DistStageAck executeOnSlave() {
      log.info("Received stop request from master...");
      if (waitForDelayed) {
         Thread t = (Thread) slaveState.get(STOP_DELAY_THREAD);
         if (t != null) {
            try {
               t.join();
               slaveState.remove(STOP_DELAY_THREAD);
            } catch (InterruptedException e) {
               return errorResponse("Interrupted while waiting for kill to be finished.", e);
            }
         } else {
            log.info("No delayed execution found in history.");
         }
      } else if (shouldExecute()) {
         if (delayExecution > 0) {
            Thread t = new Thread() {
               @Override
               public void run() {
                  try {
                     Thread.sleep(delayExecution);
                  } catch (InterruptedException e) {                    
                  }
                  if (lifecycle == null || !lifecycle.isRunning()) {
                     log.info("The service on this node is not running or cannot be stopped");
                  } else {
                     LifecycleHelper.stop(slaveState, graceful, async);
                  }
               }
            };
            slaveState.put(STOP_DELAY_THREAD, t);
            t.start();
         } else {
            if (lifecycle == null || !lifecycle.isRunning()) {
               log.info("The service on this node is not running or cannot be stopped");
            } else {
               LifecycleHelper.stop(slaveState, graceful, async);
            }
         }
      } else {
         log.info("Ignoring stop request, not targeted for this slave");
      }
      return successfulResponse();
   }
}
