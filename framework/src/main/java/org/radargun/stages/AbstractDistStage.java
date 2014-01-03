package org.radargun.stages;

import java.util.Collection;
import java.util.List;

import org.radargun.DistStage;
import org.radargun.DistStageAck;
import org.radargun.config.MasterConfig;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;
import org.radargun.utils.ClassLoadHelper;
import org.radargun.utils.Utils;

/**
 * Support class for distributed stages.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "")
public abstract class AbstractDistStage extends AbstractStage implements DistStage {

   protected Log log = LogFactory.getLog(getClass());
   private static final String PREV_PRODUCT = "AbstractDistStage.previousProduct";
   private static final String CLASS_LOADER = "AbstractDistStage.classLoader";

   protected transient SlaveState slaveState;

   protected transient MasterConfig masterConfig;

   @Property(doc = "Specifies on which slaves should this stage actively run. Default is stage-dependent (usually all or none).")
   protected Collection<Integer> slaves;

   @Property(doc = "Smart class loading loads libraries specific for the product. Default is true.")
   private boolean useSmartClassLoading = true;

   @Property(doc = "Should the benchmark fail if one of the slaves sends error acknowledgement? Default is false.")
   private boolean exitBenchmarkOnSlaveFailure = false;

   @Property(doc = "If set to true the stage should be run on maxSlaves (applies to scaling benchmarks). Default is false.")
   private boolean runOnAllSlaves;

   protected int slaveIndex;
   private int activeSlavesCount;
   private int totalSlavesCount;
   protected String productName;
   protected String configName;
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
      this.configName = masterState.configNameOfTheCurrentBenchmark();
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
       return (DistStage) super.clone();
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
         processingDuration += ack.getSlaveIndex() + " = " + Utils.prettyPrintMillis(ack.getDuration());
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

   protected Object createInstance(String classFqn) throws Exception {
      return classLoadHelper.createInstance(classFqn);
   }
}
