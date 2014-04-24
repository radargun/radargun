package org.radargun.stages.lifecycle;

import java.util.Collection;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.DefaultDistStageAck;

/**
 * Common base for stages that start slaves.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "")
public abstract class AbstractServiceStartStage extends AbstractDistStage {

   @Property(doc = "Set of slaves where the start may fail but this will not cause an error. Default is none.")
   protected Collection<Integer> mayFailOn;

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      boolean success = true;
      logDurationInfo(acks);
      for (DistStageAck stageAck : acks) {
         DefaultDistStageAck defaultStageAck = (DefaultDistStageAck) stageAck;
         if (defaultStageAck.isError() && (mayFailOn == null || !mayFailOn.contains(stageAck.getSlaveIndex()))) {
            log.warn("Received error ack " + defaultStageAck);
            return false;
         } else if (defaultStageAck.isError()) {
            log.info("Received allowed error ack " + defaultStageAck);
         } else {
            log.trace("Received success ack " + defaultStageAck);
         }
      }
      if (log.isTraceEnabled())
         log.trace("All ack messages were successful");
      return success;
   }
}
