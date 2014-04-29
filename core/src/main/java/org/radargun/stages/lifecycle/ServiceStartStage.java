package org.radargun.stages.lifecycle;

import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;

/**
 * Stage that starts a CacheWrapper on each slave.
 * 
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Starts services on specified slaves")
public class ServiceStartStage extends AbstractServiceStartStage {

   @Property(doc = "Specifies whether the cluster formation should be checked after cache wrapper startup. Default is true.")
   private boolean validateCluster = true;

   @Property(doc = "If set to true, the slaves will not be started in one moment but the startup will be delayed. Default is true.")
   private boolean staggerSlaveStartup = true;

   @Property(converter = TimeConverter.class, doc = "Delay (staggering) after first slave's start is initiated. Default is 5s")
   private long delayAfterFirstSlaveStarts = 5000;

   @Property(converter = TimeConverter.class, doc = "Delay between initiating start of i-th and (i+1)-th slave. Default is 500 ms")
   private long delayBetweenStartingSlaves = 500;

   @Property(converter = TimeConverter.class, doc = "Time allowed the cluster to reach `expectNumSlaves` members. Default is 3 minutes.")
   private long clusterFormationTimeout = 180000;

   @Property(doc = "The number of slaves that should be up after all slaves are started. Applicable only with " +
         "validateCluster=true. Default is all slaves in the cluster (in the same site in case of multi-site configuration).")
   private Integer expectNumSlaves;

   @Property(doc = "Set of slaves that should be reachable to the newly spawned slaves (see Partitionable feature for details). Default is all slaves.")
   private Set<Integer> reachable = null;

   public DistStageAck executeOnSlave() {
      if (lifecycle == null) {
         log.warn("Service " + slaveState.getServiceName() + " does not support lifecycle management.");
         return successfulResponse();
      } else if (lifecycle.isRunning()) {
         log.info("Service " + slaveState.getServiceName() + " is already running.");
         return successfulResponse();
      }
      if (slaves != null) {
         if (!slaves.contains(slaveState.getSlaveIndex())) {
            log.trace("Start request not targeted for this slave, ignoring.");
            return successfulResponse();
         } else {
            int index = 0;
            for (Integer slave : slaves) {
               if (slave.equals(slaveState.getSlaveIndex())) break;
               index++;
            }
            staggerStartup(index, slaves.size());
         }
      } else {
         staggerStartup(slaveState.getSlaveIndex(), slaveState.getClusterSize());
      }
      log.info("Ack master's StartCluster stage. Local address is: " + slaveState.getLocalAddress()
            + ". This slave's index is: " + slaveState.getSlaveIndex());
      try {
         LifecycleHelper.start(slaveState, validateCluster, expectNumSlaves, clusterFormationTimeout, reachable);
      } catch (RuntimeException e) {
         return errorResponse("Issues while instantiating/starting cache wrapper", e);
      }
      log.info("Successfully started cache service " + slaveState.getServiceName() + " on slave " + slaveState.getSlaveIndex());
      return successfulResponse();
   }

   private void staggerStartup(int thisNodeIndex, int numSlavesToStart) {
      if (!staggerSlaveStartup) {
         if (log.isTraceEnabled()) {
            log.trace("Not using slave startup staggering");
         }
         return;
      }
      if (thisNodeIndex == 0) {
         log.info("Startup staggering, number of slaves to start is " + numSlavesToStart
               + " This is the slave with index 0, not sleeping");
         return;
      }
      long toSleep = delayAfterFirstSlaveStarts + thisNodeIndex * delayBetweenStartingSlaves;
      log.info(" Startup staggering, starting " + numSlavesToStart + " slaves. This is the slave with index "
            + thisNodeIndex + ". Sleeping for " + toSleep + " millis.");
      try {
         Thread.sleep(toSleep);
      } catch (InterruptedException e) {
         throw new IllegalStateException("Should never happen");
      }
   }
}
