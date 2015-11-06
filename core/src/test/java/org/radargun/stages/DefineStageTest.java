package org.radargun.stages;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.state.SlaveState;
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
      SlaveState slaveState = stageRunner.getSlaveState();

      Assert.assertNull(slaveState.get("test"));

      DefineStage defineStage = new DefineStage();
      defineStage.var = "test";
      defineStage.value = "value";

      List<DistStageAck> acks = new ArrayList<>(1);
      acks.add(stageRunner.executeOnSlave(defineStage, 0));

      Assert.assertEquals(slaveState.get("test"), "value");
      Assert.assertEquals(stageRunner.processAckOnMaster(defineStage, acks), StageResult.SUCCESS);
   }
}
