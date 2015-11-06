package org.radargun.stages.mapreduce;

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
public class MapReduceStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      MapReduceStage mapReduceStage = new MapReduceStage();
      mapReduceStage.mapperFqn = "test";
      mapReduceStage.reducerFqn = "test";
      mapReduceStage.combinerFqn = "test";
      mapReduceStage.collatorFqn = "test";

      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);

      IntStream.range(0, 100).forEach(i -> cache.put(i, i));

      Assert.assertEquals(cache.size(), 100);

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnSlave(mapReduceStage, 0));

      Assert.assertEquals(stageRunner.processAckOnMaster(mapReduceStage, acks), StageResult.SUCCESS);
   }

   public void smokeTestWithoutCollator() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      MapReduceStage mapReduceStage = new MapReduceStage();
      mapReduceStage.mapperFqn = "test";
      mapReduceStage.reducerFqn = "test";
      mapReduceStage.combinerFqn = "test";

      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);

      IntStream.range(0, 100).forEach(i -> cache.put(i, i));

      Assert.assertEquals(cache.size(), 100);

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnSlave(mapReduceStage, 0));

      Assert.assertEquals(stageRunner.processAckOnMaster(mapReduceStage, acks), StageResult.SUCCESS);
   }
}
