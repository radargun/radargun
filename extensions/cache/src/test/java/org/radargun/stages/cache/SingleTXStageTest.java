package org.radargun.stages.cache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
public class SingleTXStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(2);

      Lifecycle lifecycle0 = stageRunner.getTraitImpl(Lifecycle.class, 0);
      lifecycle0.start();
      Lifecycle lifecycle1 = stageRunner.getTraitImpl(Lifecycle.class, 1);
      lifecycle1.start();
      Set<Integer> commitWorkers = new HashSet<>();
      commitWorkers.add(0);
      Set<Integer> commitThreads = new HashSet<>();
      commitThreads.add(0);
      SingleTXLoadStage singleTXLoadStage1 = new SingleTXLoadStage();
      singleTXLoadStage1.commitWorker = commitWorkers;
      singleTXLoadStage1.commitThread = commitThreads;
      singleTXLoadStage1.threads = 2;
      SingleTXLoadStage singleTXLoadStage2 = new SingleTXLoadStage();
      singleTXLoadStage2.commitWorker = commitWorkers;
      singleTXLoadStage2.commitThread = commitThreads;
      singleTXLoadStage2.threads = 2;

      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      stageRunner.replaceTraitImpl(BasicOperations.class, basicOperations, 1);
      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);

      Assert.assertEquals(cache.size(), 0);

      List<DistStageAck> acks = stageRunner.executeOnWorker(new SingleTXLoadStage[] {singleTXLoadStage1, singleTXLoadStage2}, new int[] {0, 1});

      Assert.assertEquals(cache.size(), 20);
      Assert.assertEquals(stageRunner.processAckOnMain(singleTXLoadStage1, acks), StageResult.SUCCESS);

      SingleTXCheckStage singleTXCheckStage1 = new SingleTXCheckStage();
      singleTXCheckStage1.commitWorker = commitWorkers;
      singleTXCheckStage1.commitThread = commitThreads;
      SingleTXCheckStage singleTXCheckStage2 = new SingleTXCheckStage();
      singleTXCheckStage2.commitWorker = commitWorkers;
      singleTXCheckStage2.commitThread = commitThreads;

      acks = stageRunner.executeOnWorker(new SingleTXCheckStage[] {singleTXCheckStage1, singleTXCheckStage2}, new int[] {0, 1});
      Assert.assertEquals(stageRunner.processAckOnMain(singleTXLoadStage1, acks), StageResult.SUCCESS);
   }
}
