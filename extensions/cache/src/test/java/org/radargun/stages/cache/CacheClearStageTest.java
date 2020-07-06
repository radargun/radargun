package org.radargun.stages.cache;

import java.util.ArrayList;
import java.util.List;

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
public class CacheClearStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);
      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);
      cache.put("test", "test");
      Assert.assertEquals(cache.size(), 1);

      ClearStage cacheClearStage = new ClearStage();

      List<DistStageAck> acks = new ArrayList<>(1);
      acks.add(stageRunner.executeOnWorker(cacheClearStage, 0));

      Assert.assertEquals(cache.size(), 0);
      Assert.assertEquals(stageRunner.processAckOnMain(cacheClearStage, acks), StageResult.SUCCESS);
   }
}
