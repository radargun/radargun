package org.radargun.stages;

import java.util.List;
import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.TestStage;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * A test stage for stopping REST operations background stage.
 *
 * @author Martin Gencur
 */
@Namespace(TestStage.NAMESPACE)
@Stage(doc = "Stage for stopping REST operations running in the background")
public class BackgroundRESTOperationsStopStage extends RESTOperationsTestStage {

   @Property(doc = "Name of the background operations to be stopped. Default is 'Test'.")
   protected String testNameToStop = "Test";

   @Override
   public void check() {
      if (duration > 0 || duration < 0) {
         duration = 0;
         log.warn("Parameter duration ignored in background stage.");
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
   public void init() {
      //do not check any parameters
   }

   @Override
   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         log.info("Not running test on this slave as service is not running.");
         return successfulResponse();
      }
      try {
         BackgroundRESTOperationsStartStage startedStage = (BackgroundRESTOperationsStartStage) slaveState.get(testNameToStop);
         if (startedStage == null) {
            throw new RuntimeException("Unable to find the test in slaveState: " + testNameToStop);
         }
         log.info("Stopping test " + startedStage.testName + " running in the background.");

         startedStage.setTerminated();

         waitForStressorsToFinish(startedStage.getStressorsManager());
         log.info("Finished test. Test duration is: " + Utils.getMillisDurationString(TimeService.currentTimeMillis() - startedStage.getStressorsManager().getStartTime()));
         return newStatisticsAck(startedStage.getStressorsManager().getStressors());
      } catch (Exception e) {
         return errorResponse("Exception while initializing the test", e);
      }
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      return processAckOnMaster(acks, testNameToStop);
   }
}
