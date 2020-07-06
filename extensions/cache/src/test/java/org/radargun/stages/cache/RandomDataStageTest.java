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
public class RandomDataStageTest {

   public void smokeTestValueCountOnly() throws Exception {
      RandomDataStage randomDataStage = new RandomDataStage();
      randomDataStage.valueCount = 100;

      smokeTest(randomDataStage);
   }

   public void smokeTestSharedWords() throws Exception {
      RandomDataStage randomDataStage = new RandomDataStage();
      randomDataStage.valueCount = 100;
      randomDataStage.stringData = true;
      randomDataStage.limitWordCount = true;
      randomDataStage.shareWords = true;
      randomDataStage.maxWordCount = 1000;

      smokeTest(randomDataStage);
   }

   public void smokeTestSharedWordsBatchSize() throws Exception {
      RandomDataStage randomDataStage = new RandomDataStage();
      randomDataStage.valueCount = 100;
      randomDataStage.stringData = true;
      randomDataStage.limitWordCount = true;
      randomDataStage.shareWords = true;
      randomDataStage.maxWordCount = 1000;
      randomDataStage.batchSize = 10;

      smokeTest(randomDataStage);
   }

   private void smokeTest(RandomDataStage randomDataStage) throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);
      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);
      Assert.assertEquals(cache.size(), 0);

      List<DistStageAck> acks = new ArrayList<>(1);
      acks.add(stageRunner.executeOnWorker(randomDataStage, 0));

      Assert.assertEquals(cache.size(), 100);
      Assert.assertEquals(stageRunner.processAckOnMain(randomDataStage, acks), StageResult.SUCCESS);
   }
}
