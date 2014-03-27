package org.radargun.stages.lifecycle;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.stages.helpers.RoleHelper;

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

   @Property(doc = "Instead of specifying concrete slaves we may choose a victim based on his role in the cluster. " +
         "Supported roles are " + RoleHelper.SUPPORTED_ROLES + ". By default no role is specified.")
   private RoleHelper.Role role;

   @Property(converter = TimeConverter.class, doc = "If this value is positive the stage will spawn a thread which " +
         "will stop the node after the delay. The stage will not wait for anything. By default the stop is immediate " +
         "and synchronous.")
   private long delayExecution;

   @Property(doc="If set, the stage will not stop any node but will wait until the delayed execution is finished. " +
         "Default is false")
   private boolean waitForDelayed = false;

   public DistStageAck executeOnSlave() {
      log.info("Received kill request from master...");
      DefaultDistStageAck ack = newDefaultStageAck();
      if (waitForDelayed) {
         Thread t = (Thread) slaveState.get(STOP_DELAY_THREAD);
         if (t != null) {
            try {
               t.join();
               slaveState.remove(STOP_DELAY_THREAD);
            } catch (InterruptedException e) {
               String error = "Interrupted while waiting for kill to be finished.";
               log.error(error, e);
               ack.setErrorMessage(error);
               ack.setRemoteException(e);
               ack.setError(true);
            }
         } else {
            log.info("No delayed execution found in history.");
         }
      } else if ((role != null && RoleHelper.hasRole(slaveState, role))
            || (slaves != null && slaves.contains(slaveState.getSlaveIndex()))) {
         if (delayExecution > 0) {
            Thread t = new Thread() {
               @Override
               public void run() {
                  try {
                     Thread.sleep(delayExecution);
                  } catch (InterruptedException e) {                    
                  }
                  LifecycleHelper.stop(slaveState, graceful, async);
               }
            };
            slaveState.put(STOP_DELAY_THREAD, t);
            t.start();
         } else {
            LifecycleHelper.stop(slaveState, graceful, async);
         }
      } else {
         log.trace("Ignoring kill request, not targeted for this slave");
      }
      return ack;
   }
}
