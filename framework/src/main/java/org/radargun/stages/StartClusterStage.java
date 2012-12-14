package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stages.helpers.StartHelper;
import org.radargun.state.MasterState;

import java.util.Set;

/**
 * Stage that starts a CacheWrapper on each slave.
 * 
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Starts cache wrappers on specified slaves")
public class StartClusterStage extends AbstractStartStage {

   @Property(doc = "Specifies whether the cluster formation should be checked after cache wrapper startup. Default is true.")
   private boolean validateCluster = true;

   @Property(doc = "If set to true, the slaves will not be started in one moment but the startup will be delayed. Default is true.")
   private boolean staggerSlaveStartup = true;

   @Property(converter = TimeConverter.class, doc = "Delay (staggering) after first slave's start is initiated. Default is 5s")
   private long delayAfterFirstSlaveStarts = 5000;

   @Property(converter = TimeConverter.class, doc = "Delay between initiating start of i-th and (i+1)-th slave. Default is 500 ms")
   private long delayBetweenStartingSlaves = 500;

   @Property(doc = "The number of slaves that should be up after all slaves are started. Applicable only with " +
         "validateCluster=true. Default is all slaves in the cluster (in the same site in case of multi-site configuration).")
   private Integer expectNumSlaves;

   @Property(doc = "Set of slaves that should be reachable to the newly spawned slaves (see Partitionable feature for details). Default is all slaves.")
   private Set<Integer> reachable = null;

   public StartClusterStage() {
      super.setExitBenchmarkOnSlaveFailure(true);
   }

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaveState.getCacheWrapper() != null) {
         log.info("Wrapper already set on this slave, not starting it again.");
         return ack;
      }
      if (slaves != null) {
         if (!slaves.contains(getSlaveIndex())) {
            log.trace("Start request not targeted for this slave, ignoring.");
            return ack;
         } else {
            int index = 0;
            for (Integer slave : slaves) {
               if (slave.equals(getSlaveIndex())) break;
               index++;
            }
            staggerStartup(index, slaves.size());
         }
      } else {
         staggerStartup(slaveIndex, getActiveSlaveCount());
      }
      log.info("Ack master's StartCluster stage. Local address is: " + slaveState.getLocalAddress()
            + ". This slave's index is: " + getSlaveIndex());
      
      StartHelper.start(productName, config, confAttributes, slaveState, getSlaveIndex(),
            validateCluster ? new StartHelper.ClusterValidation(expectNumSlaves, getActiveSlaveCount()) : null,
            reachable, classLoadHelper, ack);
      if (!ack.isError()) {
         log.info("Successfully started cache wrapper on slave " + getSlaveIndex() + ": " + slaveState.getCacheWrapper());
      }
      return ack;
   }

   @Override
   public void initOnMaster(MasterState masterState, int slaveIndex) {
      super.initOnMaster(masterState, slaveIndex);
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
