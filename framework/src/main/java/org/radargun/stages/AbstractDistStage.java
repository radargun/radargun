package org.radargun.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.DistStage;
import org.radargun.DistStageAck;
import org.radargun.config.MasterConfig;
import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;
import org.radargun.utils.Utils;

import java.util.List;

/**
 * Support class for distributed stages.
 *
 * @author Mircea.Markus@jboss.com
 */
public abstract class AbstractDistStage implements DistStage {

   protected Log log = LogFactory.getLog(getClass());

   protected transient SlaveState slaveState;

   protected transient MasterConfig masterConfig;

   protected boolean exitBenchmarkOnSlaveFailure = false;

   protected int slaveIndex;
   private int activeSlavesCount;
   private int totalSlavesCount;
   private boolean runOnAllSlaves;

   public void initOnSlave(SlaveState slaveState) {
      this.slaveState = slaveState;
   }

   public void initOnMaster(MasterState masterState, int slaveIndex) {
      this.masterConfig = masterState.getConfig();
      this.slaveIndex = slaveIndex;
      assert masterConfig != null;
      this.totalSlavesCount = masterState.getConfig().getSlaveCount();
      if (isRunOnAllSlaves()) {
         setActiveSlavesCount(totalSlavesCount);
      }

   }

   public void setRunOnAllSlaves(boolean runOnAllSlaves) {
      this.runOnAllSlaves = runOnAllSlaves;
   }


   public boolean isRunOnAllSlaves() {
      return runOnAllSlaves;
   }

   public boolean isExitBenchmarkOnSlaveFailure() {
      return exitBenchmarkOnSlaveFailure;
   }

   public void setExitBenchmarkOnSlaveFailure(boolean exitOnFailure) {
      this.exitBenchmarkOnSlaveFailure = exitOnFailure;
   }

   protected DefaultDistStageAck newDefaultStageAck() {
      return new DefaultDistStageAck(getSlaveIndex(), slaveState.getLocalAddress());
   }

   public DistStage clone() {
      try {
         return (DistStage) super.clone();
      } catch (CloneNotSupportedException e) {
         throw new IllegalStateException(e);
      }
   }

   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      boolean success = true;
      logDurationInfo(acks);
      for (DistStageAck stageAck : acks) {
         DefaultDistStageAck defaultStageAck = (DefaultDistStageAck) stageAck;
         if (defaultStageAck.isError()) {
            log.warn("Received error ack " + defaultStageAck);
            return false;
         } else {
            log.trace("Received success ack " + defaultStageAck);
         }
      }
      if (log.isTraceEnabled())
         log.trace("All ack messages were successful");
      return success;
   }

   protected void logDurationInfo(List<DistStageAck> acks) {
      if (!log.isInfoEnabled()) return;

      String processingDuration = "Durations [";
      boolean first = true;
      for (DistStageAck ack: acks) {
         if (first) first = false;
         else processingDuration += ", ";
         processingDuration += ack.getSlaveIndex() + ":" + Utils.prettyPrintMillis(ack.getDuration());
      }
      log.info("Received responses from all " + acks.size() + " slaves. " + processingDuration + "]");
   }

   public int getActiveSlaveCount() {
      return activeSlavesCount;
   }

   public void setActiveSlavesCount(int activeSlaves) {
      this.activeSlavesCount = activeSlaves;
   }

   public int getSlaveIndex() {
      return slaveIndex;
   }

   @Override
   public String toString() {
      return "slaveIndex=" + slaveIndex +
            ", activeSlavesCount=" + activeSlavesCount +
            ", totalSlavesCount=" + totalSlavesCount +
            "} ";
   }
}
