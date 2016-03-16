package org.radargun.stages.lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
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
public class ParallelStartStopStageTest {

   public void smokeTest() throws Exception {
      CoreStageRunner stageRunner = new CoreStageRunner(1);
      ParallelStartStopStage parallelStartStopStage = new ParallelStartStopStage();
      parallelStartStopStage.start = Arrays.asList(0);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      Assert.assertFalse(lifecycle.isRunning());

      List<DistStageAck> acks = new ArrayList<>(1);
      acks.add(stageRunner.executeOnSlave(parallelStartStopStage, 0));

      Assert.assertTrue(lifecycle.isRunning());
      Assert.assertEquals(stageRunner.processAckOnMaster(parallelStartStopStage, acks), StageResult.SUCCESS);
   }

   public void smokeTestStop() throws Exception {
      CoreStageRunner stageRunner = new CoreStageRunner(1);
      ParallelStartStopStage parallelStartStopStage = new ParallelStartStopStage();
      parallelStartStopStage.stop = Arrays.asList(0);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      Assert.assertTrue(lifecycle.isRunning());

      List<DistStageAck> acks = new ArrayList<>(1);
      acks.add(stageRunner.executeOnSlave(parallelStartStopStage, 0));

      Assert.assertFalse(lifecycle.isRunning());
      Assert.assertEquals(stageRunner.processAckOnMaster(parallelStartStopStage, acks), StageResult.SUCCESS);
   }
}
