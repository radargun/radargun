package org.radargun.stages.control;

import java.util.List;
import java.util.Objects;
import java.util.Stack;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Label;
import org.radargun.config.Stage;
import org.radargun.state.StateBase;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is added at the end of each repeat.",
   label = @Label(prefix = "repeat", suffix = "end"))
public class RepeatEndStage extends RepeatStage {
   @Override
   public DistStageAck executeOnSlave() {
      updateState(slaveState);
      return successfulResponse();
   }

   private void updateState(StateBase state) {
      String counterName = getCounterName();
      log.trace("Removing counter " + counterName);
      state.remove(counterName);
      Stack<String> repeatNames = (Stack<String>) state.get(RepeatStage.REPEAT_NAMES);
      if (repeatNames == null || repeatNames.isEmpty() || !Objects.equals(repeatNames.peek(), name)) {
         throw new IllegalStateException("Cannot unwind repeat: " + repeatNames);
      }
      repeatNames.pop();
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      updateState(masterState);
      return StageResult.SUCCESS;
   }
}
