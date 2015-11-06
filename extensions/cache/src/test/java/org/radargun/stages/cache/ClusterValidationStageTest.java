package org.radargun.stages.cache;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Lifecycle;
import org.radargun.util.CacheStageRunner;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class ClusterValidationStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(2);

      Lifecycle lifecycle1 = stageRunner.getTraitImpl(Lifecycle.class, 0);
      lifecycle1.start();
      Lifecycle lifecycle2 = stageRunner.getTraitImpl(Lifecycle.class, 1);
      lifecycle2.start();
      ClusterValidationStage clusterValidationStage1 = new ClusterValidationStage();
      ClusterValidationStage clusterValidationStage2 = new ClusterValidationStage();

      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      stageRunner.replaceTraitImpl(BasicOperations.class, basicOperations, 1);

      List<DistStageAck> acks = stageRunner.executeOnSlave(new ClusterValidationStage[] {clusterValidationStage1, clusterValidationStage2}, new int[] {0, 1});

      Assert.assertEquals(stageRunner.processAckOnMaster(clusterValidationStage1, acks), StageResult.SUCCESS);
   }
}
