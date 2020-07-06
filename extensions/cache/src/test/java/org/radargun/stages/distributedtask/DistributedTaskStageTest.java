package org.radargun.stages.distributedtask;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Lifecycle;
import org.radargun.util.CacheStageRunner;
import org.radargun.util.CacheTraitRepository;
import org.radargun.util.TestCallable;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class DistributedTaskStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      DistributedTaskStage distributedTaskStage = new DistributedTaskStage();
      distributedTaskStage.callable = TestCallable.class.getCanonicalName();
      distributedTaskStage.numExecutions = 10;
      distributedTaskStage.executionPolicy = "test";
      distributedTaskStage.failoverPolicy = "test";
      distributedTaskStage.nodeAddress = "localhost";

      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);

      IntStream.range(0, 100).forEach(i -> cache.put(i, i));

      Assert.assertEquals(cache.size(), 100);

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnWorker(distributedTaskStage, 0));

      Assert.assertEquals(stageRunner.processAckOnMain(distributedTaskStage, acks), StageResult.SUCCESS);
   }
}
