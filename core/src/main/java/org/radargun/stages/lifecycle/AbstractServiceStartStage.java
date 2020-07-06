package org.radargun.stages.lifecycle;

import java.util.Collection;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;

/**
 * Common base for stages that start workers.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Parent class for stages handling service start.")
public abstract class AbstractServiceStartStage extends AbstractDistStage {

   @Property(doc = "Set of workers where the start may fail but this will not cause an error. Default is none.")
   protected Collection<Integer> mayFailOn;

   @Override
   public StageResult processAckOnMain(List<DistStageAck> acks) {
      logDurationInfo(acks);
      for (DistStageAck ack : acks) {
         if (ack.isError() && (mayFailOn == null || !mayFailOn.contains(ack.getWorkerIndex()))) {
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
