package org.radargun.stages;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.state.WorkerState;
import org.radargun.util.CoreStageRunner;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class DefineStageTest {

   public void smokeTest() throws Exception {
      CoreStageRunner stageRunner = new CoreStageRunner(1);
      WorkerState workerState = stageRunner.getWorkerState();

      Assert.assertNull(workerState.get("test"));

      DefineStage defineStage = new DefineStage();
      defineStage.var = "test";
      defineStage.value = "value";

      List<DistStageAck> acks = new ArrayList<>(1);
      acks.add(stageRunner.executeOnWorker(defineStage, 0));

      Assert.assertEquals(workerState.get("test"), "value");
      Assert.assertEquals(stageRunner.processAckOnMain(defineStage, acks), StageResult.SUCCESS);
   }
}
