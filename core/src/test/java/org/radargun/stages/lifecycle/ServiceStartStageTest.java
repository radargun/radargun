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
public class ServiceStartStageTest {

   public void smokeTest() throws Exception {
      CoreStageRunner stageRunner = new CoreStageRunner(1);
      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      Assert.assertFalse(lifecycle.isRunning());

      List<DistStageAck> acks = new ArrayList<>(1);
      ServiceStartStage serviceStartStage = new ServiceStartStage();
      acks.add(stageRunner.executeOnWorker(serviceStartStage));

      Assert.assertTrue(lifecycle.isRunning());
      Assert.assertEquals(stageRunner.processAckOnMain(serviceStartStage, acks), StageResult.SUCCESS);
   }
}
