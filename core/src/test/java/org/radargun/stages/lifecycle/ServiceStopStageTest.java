package org.radargun.stages.lifecycle;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.traits.Lifecycle;
import org.radargun.util.CoreStageRunner;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class ServiceStopStageTest {

   public void smokeTest() throws Exception {
      CoreStageRunner stageRunner = new CoreStageRunner(1);
      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      Assert.assertTrue(lifecycle.isRunning());

      List<DistStageAck> acks = new ArrayList<>(1);
      ServiceStopStage serviceStopStage = new ServiceStopStage();
      acks.add(stageRunner.executeOnSlave(serviceStopStage, 0));

      Assert.assertFalse(lifecycle.isRunning());
      Assert.assertEquals(stageRunner.processAckOnMaster(serviceStopStage, acks), StageResult.SUCCESS);
   }
}
