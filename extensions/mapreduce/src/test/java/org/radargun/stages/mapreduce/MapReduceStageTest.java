package org.radargun.stages.mapreduce;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.traits.Lifecycle;
import org.radargun.util.MapReduceStageRunner;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class MapReduceStageTest {

   public void smokeTestWithCollator() throws Exception {
      smokeTest(true);
   }

   public void smokeTestWithoutCollator() throws Exception {
      smokeTest(false);
   }

   private void smokeTest(boolean useCollator) throws Exception {
      MapReduceStageRunner stageRunner = new MapReduceStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      MapReduceStage mapReduceStage = new MapReduceStage();
      mapReduceStage.mapperFqn = "test";
      mapReduceStage.reducerFqn = "test";
      mapReduceStage.combinerFqn = "test";
      mapReduceStage.collatorFqn = useCollator ? "test" : null;

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnSlave(mapReduceStage, 0));

      Assert.assertEquals(stageRunner.processAckOnMaster(mapReduceStage, acks), StageResult.SUCCESS);
   }
}
