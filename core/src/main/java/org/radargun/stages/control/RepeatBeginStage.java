package org.radargun.stages.control;

import java.util.List;
import java.util.Stack;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Label;
import org.radargun.config.Stage;
import org.radargun.state.MainListener;
import org.radargun.state.StateBase;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is added at the beginning of each repeat.",
   label = @Label(prefix = "repeat", suffix = "begin"))
public class RepeatBeginStage extends RepeatStage {

   @Override
   public DistStageAck executeOnWorker() {
      updateState(workerState);
      return successfulResponse();
   }

   @Override
   public StageResult processAckOnMain(List<DistStageAck> acks) {
      mainState.addListener(new MainListener() {
         @Override
         public void afterCluster() {
            mainState.remove(getCounterName());
            mainState.removeListener(this);
         }
      });
      int value = updateState(mainState);
      if (inc == 0) {
         log.error("Invalid increment value: " + inc);
         return errorResult();
      }
      if ((inc > 0 && value > to) || (inc < 0 && value < to)) {
         return StageResult.BREAK;
      } else {
         return StageResult.SUCCESS;
      }
   }

   private int updateState(StateBase state) {
      String counterName = getCounterName();
      Integer value = (Integer) state.get(counterName);
      int newValue;
      if (value == null) {
         Stack<String> repeatNames = (Stack<String>) state.get(REPEAT_NAMES);
         if (repeatNames == null) {
            repeatNames = new Stack<>();
            state.put(RepeatStage.REPEAT_NAMES, repeatNames);
         }
         repeatNames.push(name);
         newValue = from;
      } else {
         newValue = value + inc;
      }
      state.put(counterName, newValue);
      log.trace("Set " + counterName + " to " + newValue);
      return newValue;
   }

}
