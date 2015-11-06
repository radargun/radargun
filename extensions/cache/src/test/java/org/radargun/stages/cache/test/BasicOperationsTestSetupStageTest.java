package org.radargun.stages.cache.test;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.stages.cache.generators.ByteArrayValueGenerator;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.test.TestSetupStage;
import org.radargun.stages.test.TestStage;
import org.radargun.state.SlaveState;
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
public class BasicOperationsTestSetupStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);
      SlaveState slaveState = stageRunner.getSlaveState();
      slaveState.put(KeyGenerator.KEY_GENERATOR, new StringKeyGenerator());
      slaveState.put(ValueGenerator.VALUE_GENERATOR, new ByteArrayValueGenerator());

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      BasicOperationsTestSetupStage basicOperationsTestSetupStage = new BasicOperationsTestSetupStage();
      basicOperationsTestSetupStage.rampUpMinSteadyPeriod = 3000;
      basicOperationsTestSetupStage.numEntries = 100;

      TestSetupStage.InvocationSetting putInvocationSetting = new TestSetupStage.InvocationSetting();
      putInvocationSetting.interval = 100;
      putInvocationSetting.invocations = 1;
      basicOperationsTestSetupStage.put = putInvocationSetting;

      TestSetupStage.InvocationSetting getInvocationSetting = new TestSetupStage.InvocationSetting();
      getInvocationSetting.interval = 100;
      getInvocationSetting.invocations = 4;
      basicOperationsTestSetupStage.get = getInvocationSetting;

      List<DistStageAck> acks = new ArrayList<>(1);

      long start = System.currentTimeMillis();
      acks.add(stageRunner.executeOnSlave(basicOperationsTestSetupStage, 0));
      long end = System.currentTimeMillis();

      Assert.assertTrue(end - start >= 3000);
      Assert.assertEquals(stageRunner.processAckOnMaster(basicOperationsTestSetupStage, acks), StageResult.SUCCESS);

      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);

      Assert.assertNotNull(cache);
      Assert.assertTrue(cache.size() > 0);

      TestStage testStage = new TestStage();
      testStage.duration = 3000;

      acks = new ArrayList<>(1);

      start = System.currentTimeMillis();
      acks.add(stageRunner.executeOnSlave(testStage, 0));
      end = System.currentTimeMillis();

      Assert.assertTrue(end - start >= 3000);
      Assert.assertEquals(stageRunner.processAckOnMaster(testStage, acks), StageResult.SUCCESS);
   }

}
