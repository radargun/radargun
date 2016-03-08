package org.radargun.stages.lifecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.helpers.RoleHelper;
import org.radargun.utils.TimeConverter;

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

   @Property(doc = "Set of roles which should be stopped in this stage. Default is empty.")
   private Set<RoleHelper.Role> stopRoles = new HashSet<>();

   @Property(converter = TimeConverter.class, doc = "Delay before the slaves are started. Default is 0.")
   private long startDelay = 0;

   @Property(doc = "Applicable only for cache wrappers with Partitionable feature. Set of slaves that should be " +
      "reachable from the new node. Default is all slaves.")
   private Set<Integer> reachable = null;

   @Override
   public DistStageAck executeOnSlave() {
      if (lifecycle == null) {
         log.warn("No lifecycle for service " + slaveState.getServiceName());
         return successfulResponse();
      }
      boolean stopMe = stop.contains(slaveState.getSlaveIndex()) || RoleHelper.hasAnyRole(slaveState, stopRoles);
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
}
