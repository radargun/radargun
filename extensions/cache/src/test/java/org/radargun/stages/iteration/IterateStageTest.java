package org.radargun.stages.iteration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Lifecycle;
import org.radargun.util.CacheStageRunner;
import org.radargun.util.CacheTraitRepository;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class IterateStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      IterateStage iterateStage = new IterateStage();
      iterateStage.duration = 1000;
      iterateStage.totalThreads = 10;

      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);

      IntStream.range(0, 100).forEach(i -> cache.put(i, i));

      Assert.assertEquals(cache.size(), 100);

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnSlave(iterateStage, 0));

      Assert.assertEquals(stageRunner.processAckOnMaster(iterateStage, acks), StageResult.SUCCESS);
   }
}
