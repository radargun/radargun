package org.radargun.stages.cache.test;

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
public class CacheLoadStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);
      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();

      List<DistStageAck> acks = new ArrayList<>(1);
      LoadStage cacheLoadStage = new LoadStage();
      acks.add(stageRunner.executeOnWorker(cacheLoadStage, 0));
      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);

      Assert.assertNotNull(cache);
      Assert.assertEquals(cache.size(), 100);
      Assert.assertEquals(stageRunner.processAckOnMain(cacheLoadStage, acks), StageResult.SUCCESS);
   }
}
