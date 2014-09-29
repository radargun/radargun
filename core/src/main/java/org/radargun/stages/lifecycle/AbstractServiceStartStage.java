package org.radargun.stages.lifecycle;

import java.util.Collection;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;

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
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      boolean success = true;
      logDurationInfo(acks);
      for (DistStageAck ack : acks) {
         if (ack.isError() && (mayFailOn == null || !mayFailOn.contains(ack.getSlaveIndex()))) {
            log.warn("Received error ack " + ack);
            return errorResult();
         } else if (ack.isError()) {
            log.info("Received allowed error ack " + ack);
         } else {
            log.trace("Received success ack " + ack);
         }
      }
      if (log.isTraceEnabled())
         log.trace("All ack messages were successful");
      return StageResult.SUCCESS;
   }
}
