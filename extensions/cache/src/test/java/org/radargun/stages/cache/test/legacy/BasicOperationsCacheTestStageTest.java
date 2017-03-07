package org.radargun.stages.cache.test.legacy;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.stages.cache.generators.ByteArrayValueGenerator;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.cache.test.BasicOperationsTestStage;
import org.radargun.stages.cache.test.ConcurrentKeysSelector;
import org.radargun.stages.cache.test.KeySelectorFactory;
import org.radargun.stages.helpers.CacheSelector;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Lifecycle;
import org.radargun.util.CacheStageRunner;
import org.radargun.util.CacheTraitRepository;
import org.radargun.utils.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class BasicOperationsCacheTestStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);
      SlaveState slaveState = stageRunner.getSlaveState();
      slaveState.put(KeyGenerator.KEY_GENERATOR, new StringKeyGenerator());
      slaveState.put(ValueGenerator.VALUE_GENERATOR, new ByteArrayValueGenerator());
      slaveState.put(CacheSelector.CACHE_SELECTOR, new CacheSelector.Default());

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      BasicOperationsTestStage basicOperationsLegacyTestStage = new BasicOperationsTestStage();
      KeySelectorFactory keySelectorFactory = new ConcurrentKeysSelector.Factory();
      Utils.setField(ConcurrentKeysSelector.Factory.class, "totalEntries", keySelectorFactory, 100);
      Utils.setField(BasicOperationsTestStage.class, "keySelectorFactory", basicOperationsLegacyTestStage, keySelectorFactory);
      Utils.setField(BasicOperationsTestStage.class, "duration", basicOperationsLegacyTestStage, 3000);
      Utils.setField(BasicOperationsTestStage.class, "totalThreads", basicOperationsLegacyTestStage, 10);

      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);

      Assert.assertEquals(cache.size(), 0);

      List<DistStageAck> acks = new ArrayList<>(1);

      long start = System.currentTimeMillis();
      acks.add(stageRunner.executeOnSlave(basicOperationsLegacyTestStage, 0));
      long end = System.currentTimeMillis();

      Assert.assertTrue(end - start >= 3000);
      Assert.assertTrue(cache.size() > 0);
      Assert.assertEquals(stageRunner.processAckOnMaster(basicOperationsLegacyTestStage, acks), StageResult.SUCCESS);
   }
}
