package org.radargun.stages.cache;

import java.util.Arrays;
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
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.Lifecycle;
import org.radargun.util.CacheStageRunner;
import org.radargun.util.CacheTraitRepository;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class XSReplCheckStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(2);

      StringKeyGenerator keyGenerator = new StringKeyGenerator();
      ByteArrayValueGenerator valueGenerator = new ByteArrayValueGenerator();

      SlaveState slaveState = stageRunner.getSlaveState(0);
      slaveState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      slaveState.put(ValueGenerator.VALUE_GENERATOR, valueGenerator);
      slaveState.put(CacheSelector.CACHE_SELECTOR, new CacheSelector.Default());

      slaveState = stageRunner.getSlaveState(1);
      slaveState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      slaveState.put(ValueGenerator.VALUE_GENERATOR, valueGenerator);
      slaveState.put(CacheSelector.CACHE_SELECTOR, new CacheSelector.Default());

      Lifecycle lifecycle1 = stageRunner.getTraitImpl(Lifecycle.class, 0);
      lifecycle1.start();
      Lifecycle lifecycle2 = stageRunner.getTraitImpl(Lifecycle.class, 0);
      lifecycle2.start();

      XSReplCheckStage xsReplCheckStage1 = new XSReplCheckStage();
      xsReplCheckStage1.backupValueGenerator = valueGenerator;
      xsReplCheckStage1.backupCaches = Arrays.asList("test");
      xsReplCheckStage1.numEntries = 100;
      xsReplCheckStage1.entrySize = 1024;

      XSReplCheckStage xsReplCheckStage2 = new XSReplCheckStage();
      xsReplCheckStage2.backupValueGenerator = valueGenerator;
      xsReplCheckStage2.backupCaches = Arrays.asList("test");
      xsReplCheckStage2.numEntries = 100;
      xsReplCheckStage2.entrySize = 1024;

      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      stageRunner.replaceTraitImpl(BasicOperations.class, basicOperations, 1);
      CacheInformation cacheInformation = stageRunner.getTraitImpl(CacheInformation.class);
      stageRunner.replaceTraitImpl(CacheInformation.class, cacheInformation, 1);

      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);
      Random random = new Random(123);

      IntStream.range(0, 100).forEach(i -> {
         Object key = keyGenerator.generateKey(i);
         Object value = valueGenerator.generateValue(key, 1024, random);
         cache.put(key, value);
      });

      Assert.assertEquals(cache.size(), 100);

      List<DistStageAck> acks = stageRunner.executeOnSlave(new XSReplCheckStage[] {xsReplCheckStage1, xsReplCheckStage2}, new int[] {0, 1});

      Assert.assertEquals(stageRunner.processAckOnMaster(xsReplCheckStage1, acks), StageResult.SUCCESS);
   }
}
