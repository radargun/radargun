package org.radargun.stages.query;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.traits.Lifecycle;
import org.radargun.util.QueryStageRunner;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class ReindexStageTest {

   public void smokeTest() throws Exception {
      QueryStageRunner stageRunner = new QueryStageRunner(1);
      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();

      ReindexStage reindexStage = new ReindexStage();

      List<DistStageAck> acks = new ArrayList<>(1);
      acks.add(stageRunner.executeOnSlave(reindexStage, 0));

      Assert.assertEquals(stageRunner.processAckOnMaster(reindexStage, acks), StageResult.SUCCESS);
   }
}
