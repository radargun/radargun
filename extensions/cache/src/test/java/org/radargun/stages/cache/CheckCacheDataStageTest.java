package org.radargun.stages.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.stages.cache.generators.ByteArrayValueGenerator;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.helpers.CacheSelector;
import org.radargun.state.WorkerState;
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
public class CheckCacheDataStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);
      WorkerState workerState = stageRunner.getWorkerState();

      StringKeyGenerator keyGenerator = new StringKeyGenerator();
      ByteArrayValueGenerator valueGenerator = new ByteArrayValueGenerator();

      workerState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      workerState.put(ValueGenerator.VALUE_GENERATOR, valueGenerator);
      workerState.put(CacheSelector.CACHE_SELECTOR, new CacheSelector.Default());

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      CheckCacheDataStage checkCacheDataStage = new CheckCacheDataStage();
      checkCacheDataStage.numEntries = 100;
      checkCacheDataStage.entrySize = 1024;

      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);
      Random random = new Random(123);

      IntStream.range(0, 100).forEach(i -> {
         Object key = keyGenerator.generateKey(i);
         Object value = valueGenerator.generateValue(key, 1024, random);
         cache.put(key, value);
      });

      Assert.assertEquals(cache.size(), 100);

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnWorker(checkCacheDataStage, 0));

      Assert.assertEquals(stageRunner.processAckOnMain(checkCacheDataStage, acks), StageResult.SUCCESS);
   }
}
