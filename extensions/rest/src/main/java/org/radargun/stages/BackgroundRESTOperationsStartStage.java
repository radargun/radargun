package org.radargun.stages;

import java.util.List;
import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Namespace;
import org.radargun.config.Stage;
import org.radargun.stages.test.Completion;
import org.radargun.stages.test.TimeStressorCompletion;

/**
 * A stage for REST operations running in background.
 *
 * @author Martin Gencur
 */
@Namespace(RESTOperationsTestStage.NAMESPACE)
@Stage(doc = "Stage for starting REST operations in the background")
public class BackgroundRESTOperationsStartStage extends RESTOperationsTestStage {

   //Override Init method from BaseTestStage in order to handle duration specifically
   @Override
   public void check() {
      if (duration > 0 || duration < 0) {
         duration = 0;
         log.warn("Parameter duration ignored in background stage. Stage will run indefinitely until " +
            "it is manually stopped from " + BackgroundRESTOperationsStopStage.class.getSimpleName());
      }
      if (numOperations > 0) {
         numOperations = 0;
         log.warn("Parameter numOperations ignored in background stage.");
      }
      if (timeout > 0 || timeout < 0) {
         timeout = 0;
         log.warn("Parameter timeout ignored in background stage.");
      }
   }

   @Override
   protected Completion createCompletion() {
      return new TimeStressorCompletion(duration);
   }

   @Override
   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         log.info("Not running test on this slave as service is not running.");
         return successfulResponse();
      }
      try {
         log.info("Starting test " + testName + " in the background.");
         stressorsManager = setUpAndStartStressors();
         slaveState.put(testName, this);
         return successfulResponse();
      } catch (Exception e) {
         return errorResponse("Exception while initializing the test", e);
      }
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = StageResult.SUCCESS;
      logDurationInfo(acks);
      for (DistStageAck ack : acks) {
         if (ack.isError()) {
            log.warn("Received error ack " + ack);
            result = errorResult();
         } else {
            if (log.isTraceEnabled()) {
               log.trace("Received success ack " + ack);
            }
         }
      }
      return result;
   }
}
