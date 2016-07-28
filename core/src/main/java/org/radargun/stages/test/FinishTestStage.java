package org.radargun.stages.test;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;

@Stage(doc = "Stops stressor threads and cleans-up after multi-iteration test stages.")
public class FinishTestStage extends AbstractDistStage {
   @Property(doc = "Name of the test as used for reporting. Default is 'Test'.")
   protected String testName = "Test";

   @Override
   public DistStageAck executeOnSlave() {
      String key = RunningTest.nameFor(testName);
      RunningTest runningTest = (RunningTest) slaveState.get(key);
      if (runningTest != null) {
         runningTest.stopStressors();
         slaveState.remove(key);
         slaveState.removeServiceListener(runningTest);
         return successfulResponse();
      } else {
         log.info("Slave state: " + slaveState.asStringMap());
         return errorResponse("No test " + testName + " running");
      }
   }
}
