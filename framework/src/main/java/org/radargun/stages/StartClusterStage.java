package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.state.MasterState;

/**
 * Stage that starts a CacheWrapper on each slave.
 * 
 * @author Mircea.Markus@jboss.com
 */
public class StartClusterStage extends AbstractStartStage {

   private boolean performClusterSizeValidation = true;
   private boolean staggerSlaveStartup = true;
   private long delayAfterFirstSlaveStarts = 5000;
   private long delayBetweenStartingSlaves = 500;
   private Integer expectNumSlaves;

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
            staggerStartup(slaves.indexOf(getSlaveIndex()), slaves.size());
         }
      } else {
         staggerStartup(slaveIndex, getActiveSlaveCount());
      }
      log.info("Ack master's StartCluster stage. Local address is: " + slaveState.getLocalAddress()
            + ". This slave's index is: " + getSlaveIndex());
      
      int expectedSlaves = expectNumSlaves == null ? getActiveSlaveCount() : expectNumSlaves;
      StartHelper.start(productName, config, confAttributes, slaveState, getSlaveIndex(),
            performClusterSizeValidation, expectedSlaves, classLoadHelper, ack);
      if (!ack.isError()) {
         log.info("Successfully started cache wrapper on slave " + getSlaveIndex() + ": " + slaveState.getCacheWrapper());
      }
      return ack;
   }

   public void setPerformCLusterSizeValidation(boolean performCLusterSizeValidation) {
      this.performClusterSizeValidation = performCLusterSizeValidation;
   }

   @Override
   public void initOnMaster(MasterState masterState, int slaveIndex) {
      super.initOnMaster(masterState, slaveIndex);
   }

   @Override
   public String toString() {
      return "StartClusterStage {config=" + config + ", " + super.toString();
   }

   public void setStaggerSlaveStartup(boolean staggerSlaveStartup) {
      this.staggerSlaveStartup = staggerSlaveStartup;
   }

   public void setDelayAfterFirstSlaveStarts(long delayAfterFirstSlaveStarts) {
      this.delayAfterFirstSlaveStarts = delayAfterFirstSlaveStarts;
   }

   public void setDelayBetweenStartingSlaves(long delayBetweenSlavesStarts) {
      this.delayBetweenStartingSlaves = delayBetweenSlavesStarts;
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

   public void setExpectNumSlaves(int numSlaves) {
      this.expectNumSlaves = numSlaves;
   }
   
}
