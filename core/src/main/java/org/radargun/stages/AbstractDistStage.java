package org.radargun.stages;

import java.util.Collection;
import java.util.List;

import org.radargun.DistStage;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Lifecycle;
import org.radargun.utils.Utils;

/**
 * Support class for distributed stages.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "")
public abstract class AbstractDistStage extends AbstractStage implements DistStage {
   protected Log log = LogFactory.getLog(getClass());

   /**
    * This field is filled in only on master node, on slave it is set to null
    */
   protected MasterState masterState;
   /**
    * This field is filled in only on slave node, on master it is set to null
    */
   protected SlaveState slaveState;

   @Property(doc = "Specifies on which slaves should this stage actively run. Default is stage-dependent (usually all or none).")
   protected Collection<Integer> slaves;

   @Property(doc = "If set to true the stage should be run on maxSlaves (applies to scaling benchmarks). Default is false.")
   private boolean runOnAllSlaves;

   @InjectTrait
   protected Lifecycle lifecycle;

   @Override
   public void initOnMaster(MasterState masterState) {
      this.masterState = masterState;
   }

   @Override
   public void initOnSlave(SlaveState slaveState) {
      this.slaveState = slaveState;
   }

   public boolean isServiceRunnning() {
      return lifecycle == null || lifecycle.isRunning();
   }

   public boolean isRunOnAllSlaves() {
      return runOnAllSlaves;
   }

   protected DefaultDistStageAck newDefaultStageAck() {
      return new DefaultDistStageAck(slaveState.getSlaveIndex(), slaveState.getLocalAddress());
   }

   public boolean processAckOnMaster(List<DistStageAck> acks) {
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

   protected DistStageAck errorResponse(String message, Exception e) {
      return newDefaultStageAck().error(message, e);
   }

   protected DistStageAck successfulResponse() {
      return newDefaultStageAck();
   }
}
