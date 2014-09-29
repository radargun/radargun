package org.radargun.stages.control;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Stage;

/**
 * This should just redirect us to the beginning of the loop.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is added at the end of each repeat.")
public class RepeatContinueStage extends RepeatStage {
   @Override
   public DistStageAck executeOnSlave() {
      return successfulResponse();
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      return StageResult.CONTINUE;
   }
}
