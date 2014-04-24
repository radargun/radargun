package org.radargun.stages.lifecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.stages.helpers.RoleHelper;

/**
 * The stage start and kills some nodes concurrently (without waiting for each other).
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "The stage start and stops some nodes concurrently (without waiting for each other).")
public class ParallelStartStopStage extends AbstractServiceStartStage {

   @Property(doc = "Set of slaves which should be stopped in this stage. Default is empty.")
   private Collection<Integer> stop = new ArrayList<Integer>();

   @Property(converter = TimeConverter.class, doc = "Delay before the slaves are stopped. Default is 0.")
   private long stopDelay = 0;

   @Property(doc = "If set to false, the node crash should be simulated. By default node should be shutdown gracefully.")
   private boolean graceful = true;

   @Property(doc = "Set of slaves which should be started in this stage. Default is empty.")
   private Collection<Integer> start = new ArrayList<Integer>();

   @Property(converter = TimeConverter.class, doc = "Delay before the slaves are started. Default is 0.")
   private long startDelay = 0;

   @Property(doc = "Applicable only for cache wrappers with Partitionable feature. Set of slaves that should be" +
         "reachable from the new node. Default is all slaves.")
   private Set<Integer> reachable = null;

   /* Note: having role for start has no sense as the dead nodes cannot have any role in the cluster */
   @Property(doc = "Another way how to specify stopped nodes is by role. Available roles are "
         + RoleHelper.SUPPORTED_ROLES + ". By default this is not used.")
   private RoleHelper.Role role;

   @Override
   public DistStageAck executeOnSlave() {
      if (lifecycle == null) {
         log.warn("No lifecycle for service " + slaveState.getServiceName());
         return successfulResponse();
      }
      boolean stopMe = stop.contains(slaveState.getSlaveIndex()) || RoleHelper.hasRole(slaveState, role);
      boolean startMe = start.contains(slaveState.getSlaveIndex());
      if (!(stopMe || startMe)) {
         log.info("Nothing to kill or start...");
      }
      while (stopMe || startMe) {
         if (startMe) {
            if (lifecycle.isRunning()) {
               if (!stopMe) {
                  log.info("Wrapper already set on this slave, not starting it again.");
                  startMe = false;
                  return successfulResponse();
               } 
            } else {
               if (startDelay > 0) {
                  try {
                     Thread.sleep(startDelay);
                  } catch (InterruptedException e) {
                     log.error("Starting delay was interrupted.", e);
                  }
               }
               try {
                  LifecycleHelper.start(slaveState, false, null, 0, reachable);
               } catch (RuntimeException e) {
                  return errorResponse("Issues while instantiating/starting cache wrapper", e);
               }
               startMe = false;
            }            
         }
         if (stopMe) {
            if (!lifecycle.isRunning()) {
               if (!startMe) {
                  log.info("Wrapper is dead, nothing to kill");
                  stopMe = false;
                  return successfulResponse();
               }
            } else {
               try {
                  Thread.sleep(stopDelay);
               } catch (InterruptedException e) {
                  log.error("Killing delay was interrupted.", e);
               }
               try {
                  LifecycleHelper.stop(slaveState, graceful, false);
               } catch (RuntimeException e) {
                  return errorResponse("Failed to kill the service", e);
               }
               stopMe = false;
            }
         }
      }
      return successfulResponse();
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      boolean success = true;
      logDurationInfo(acks);
      for (DistStageAck stageAck : acks) {
         DefaultDistStageAck defaultStageAck = (DefaultDistStageAck) stageAck;
         if (defaultStageAck.isError() && (mayFailOn == null || !mayFailOn.contains(stageAck.getSlaveIndex()))) {
            log.warn("Received error ack " + defaultStageAck);
            return false;
         } else if (defaultStageAck.isError()) {
            log.info("Received allowed error ack " + defaultStageAck);
         } else {
            log.trace("Received success ack " + defaultStageAck);
         }
      }
      if (log.isTraceEnabled())
         log.trace("All ack messages were successful");
      return success;
   }
}
