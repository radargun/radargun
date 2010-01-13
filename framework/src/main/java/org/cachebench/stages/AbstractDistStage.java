package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.DistStage;
import org.cachebench.DistStageAck;
import org.cachebench.config.MasterConfig;
import org.cachebench.state.SlaveState;
import org.cachebench.state.MasterState;
import org.cachebench.utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Support class for distributed stages.
 *
 * @author Mircea.Markus@jboss.com
 */
public abstract class AbstractDistStage implements DistStage {

   protected Log log = LogFactory.getLog(getClass());

   protected transient SlaveState slaveState;

   protected transient MasterConfig masterConfig;

   protected transient MasterState masterState;

   protected int slaveIndex;
   private int activeSlavesCount;
   private int totalSlavesCount;
   private boolean runOnAllSlaves;

   public void initOnSlave(SlaveState slaveState) {
      this.slaveState = slaveState;
   }

   public void initOnMaster(MasterState masterState, int totalSlavesCount) {
      this.masterState = masterState;
      this.masterConfig = masterState.getConfig();
      assert masterConfig != null;
      this.totalSlavesCount = totalSlavesCount;
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

   public void setSlaveIndex(int index) {
      this.slaveIndex = index;
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

   public boolean processAckOnMaster(List<DistStageAck> acks) {
      boolean success = true;
      logDurationInfo(acks);
      for (DistStageAck stageAck : acks) {
         DefaultDistStageAck defaultStageAck = (DefaultDistStageAck) stageAck;
         if (defaultStageAck.isError()) {
            success = false;
            log.warn("Received error ack " + defaultStageAck, defaultStageAck.getRemoteException());
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

      Map<Integer, String> data = new TreeMap<Integer, String>();  // make sure this is sorted
      for (DistStageAck dsa: acks) data.put(dsa.getSlaveIndex(), Utils.prettyPrintTime(dsa.getDuration()));

      String processingDuration = "Durations [";
      boolean first = true;
      for (Map.Entry<Integer, String> e: data.entrySet()) {
         if (first) first = false;
         else processingDuration += ", ";
         processingDuration += e.getKey() + ":" + e.getValue();
      }
      log.info(getClass().getSimpleName() + " received ack from all (" + acks.size() + ") slaves. " + processingDuration + "]");
   }

   public int getActiveSlaveCount() {
      return activeSlavesCount;
   }

   public int getTotalSlavesCount() {
      return totalSlavesCount;
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
