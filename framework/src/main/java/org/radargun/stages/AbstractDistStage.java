package org.radargun.stages;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.DistStage;
import org.radargun.DistStageAck;
import org.radargun.config.MasterConfig;
import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;
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
      this.slaves = new ArrayList<Integer>();
      for (String slave : slaves.split(",")) {
         this.slaves.add(Integer.valueOf(slave));
      }
   }

   public void setUseSmartClassLoading(boolean useSmartClassLoading) {
      this.useSmartClassLoading = useSmartClassLoading;
   }

   protected Object createInstance(String classFqn) throws Exception {
      if (!useSmartClassLoading) {
         return Class.forName(classFqn).newInstance();
      }
      URLClassLoader classLoader;
      String prevProduct = (String) slaveState.get(PREV_PRODUCT);
      if (prevProduct == null || !prevProduct.equals(productName)) {
         classLoader = createLoader();
         slaveState.put(CLASS_LOADER, classLoader);
         slaveState.put(PREV_PRODUCT, productName);
      } else {//same product and there is a class loader
         classLoader = (URLClassLoader) slaveState.get(CLASS_LOADER);
      }
      log.info("Creating newInstance " + classFqn + " with classloader " + classLoader);
      Thread.currentThread().setContextClassLoader(classLoader);
      return classLoader.loadClass(classFqn).newInstance();
   }

   private URLClassLoader createLoader() throws Exception {
      return Utils.buildProductSpecificClassLoader(productName, this.getClass().getClassLoader());
   }

}
