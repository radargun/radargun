package org.radargun.stages;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.DistStage;
import org.radargun.DistStageAck;
import org.radargun.config.MasterConfig;
import org.radargun.stages.helpers.ParseHelper;
import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;
import org.radargun.utils.ClassLoadHelper;
import org.radargun.utils.Utils;

/**
 * Support class for distributed stages.
 *
 * @author Mircea.Markus@jboss.com
 */
public abstract class AbstractDistStage implements DistStage {

   protected Log log = LogFactory.getLog(getClass());
   private static final String PREV_PRODUCT = "AbstractDistStage.previousProduct";
   private static final String CLASS_LOADER = "AbstractDistStage.classLoader";

   protected transient SlaveState slaveState;

   protected transient MasterConfig masterConfig;
   protected List<Integer> slaves;

   protected boolean exitBenchmarkOnSlaveFailure = false;

   protected int slaveIndex;
   private int activeSlavesCount;
   private int totalSlavesCount;
   private boolean runOnAllSlaves;
   private boolean useSmartClassLoading = true;
   protected String productName;
   protected ClassLoadHelper classLoadHelper;

   public void initOnSlave(SlaveState slaveState) {
      this.slaveState = slaveState;
      classLoadHelper = new ClassLoadHelper(useSmartClassLoading, getClass(), productName, slaveState, PREV_PRODUCT, CLASS_LOADER);
   }

   public void initOnMaster(MasterState masterState, int slaveIndex) {
      this.masterConfig = masterState.getConfig();
      this.slaveIndex = slaveIndex;
      assert masterConfig != null;
      this.totalSlavesCount = masterState.getConfig().getSlaveCount();
      if (isRunOnAllSlaves()) {
         setActiveSlavesCount(totalSlavesCount);
      }
      this.productName = masterState.nameOfTheCurrentBenchmark();      
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
      return "productName='" + productName + "', useSmartClassLoading=" + useSmartClassLoading + ", slaveIndex="
            + slaveIndex + ", activeSlavesCount=" + activeSlavesCount + ", totalSlavesCount=" + totalSlavesCount
            + (slaves == null ? "}" : ", slaves=" + slaves + "}");
   }

   public void setSlaves(String slaves) {
      this.slaves = ParseHelper.parseList(slaves, "slaves", log);
   }

   public void setUseSmartClassLoading(boolean useSmartClassLoading) {
      this.useSmartClassLoading = useSmartClassLoading;
   }

   protected Object createInstance(String classFqn) throws Exception {
      return classLoadHelper.createInstance(classFqn);
   }
}
