package org.radargun.stages.cache.background;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.Lifecycle;
import org.radargun.util.CacheStageRunner;
import org.radargun.util.CacheTraitRepository;
import org.radargun.utils.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 60000)
public class BackgroundStressorsGeneralTest {

   public void smokeTestNonSharedKeys() throws Exception {
      smokeTest(false);
   }

   public void smokeTestSharedKeys() throws Exception {
      smokeTest(true);
   }

   private void smokeTest(boolean sharedKeys) throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(2);
      Lifecycle lifecycle1 = stageRunner.getTraitImpl(Lifecycle.class, 0);
      Lifecycle lifecycle2 = stageRunner.getTraitImpl(Lifecycle.class, 1);
      lifecycle1.start();
      lifecycle2.start();
      BackgroundStressorsStartStage backgroundStressorsStartStageWorker1 = new BackgroundStressorsStartStage();
      BackgroundStressorsStartStage backgroundStressorsStartStageWorker2 = new BackgroundStressorsStartStage();

      LogLogicConfiguration logLogicConfiguration = new LogLogicConfiguration();
      logLogicConfiguration.enabled = true;
      backgroundStressorsStartStageWorker1.logLogicConfiguration = logLogicConfiguration;
      backgroundStressorsStartStageWorker2.logLogicConfiguration = logLogicConfiguration;

      GeneralConfiguration generalConfiguration = new GeneralConfiguration();
      generalConfiguration.puts = 2;
      generalConfiguration.removes = 1;
      generalConfiguration.gets = 0;
      generalConfiguration.sharedKeys = sharedKeys;
      backgroundStressorsStartStageWorker1.generalConfiguration = generalConfiguration;
      backgroundStressorsStartStageWorker2.generalConfiguration = generalConfiguration;

      BasicOperations basicOperations = stageRunner.getTraitImpl(BasicOperations.class);
      stageRunner.replaceTraitImpl(BasicOperations.class, basicOperations, 1);
      ConditionalOperations conditionalOperations = stageRunner.getTraitImpl(ConditionalOperations.class);
      stageRunner.replaceTraitImpl(ConditionalOperations.class, conditionalOperations, 1);

      CacheTraitRepository.BasicOperationsCache cache = (CacheTraitRepository.BasicOperationsCache) basicOperations.getCache(null);
      Assert.assertEquals(cache.size(), 0);

      List<DistStageAck> acks = new ArrayList<>(2);
      acks.add(stageRunner.executeOnWorker(backgroundStressorsStartStageWorker1, 0));
      acks.add(stageRunner.executeOnWorker(backgroundStressorsStartStageWorker2, 1));

      Assert.assertEquals(stageRunner.processAckOnMain(backgroundStressorsStartStageWorker1, acks), StageResult.SUCCESS);

      Utils.sleep(3000);

      Assert.assertTrue(cache.size() > 0);

      BackgroundStressorsCheckStage backgroundStressorsCheckStage1 = new BackgroundStressorsCheckStage();
      BackgroundStressorsCheckStage backgroundStressorsCheckStage2 = new BackgroundStressorsCheckStage();
      backgroundStressorsCheckStage1.waitUntilChecked = true;
      backgroundStressorsCheckStage2.waitUntilChecked = true;

      acks = new ArrayList<>(2);
      acks.add(stageRunner.executeOnWorker(backgroundStressorsCheckStage1, 0));
      acks.add(stageRunner.executeOnWorker(backgroundStressorsCheckStage2, 1));

      Assert.assertEquals(stageRunner.processAckOnMain(backgroundStressorsCheckStage1, acks), StageResult.SUCCESS);

      BackgroundStressorsCheckStage backgroundStressorsCheckStage3 = new BackgroundStressorsCheckStage();
      BackgroundStressorsCheckStage backgroundStressorsCheckStage4 = new BackgroundStressorsCheckStage();
      backgroundStressorsCheckStage3.resumeAfterChecked = true;
      backgroundStressorsCheckStage4.resumeAfterChecked = true;

      acks = new ArrayList<>(2);
      acks.add(stageRunner.executeOnWorker(backgroundStressorsCheckStage3, 0));
      acks.add(stageRunner.executeOnWorker(backgroundStressorsCheckStage4, 1));

      Assert.assertEquals(stageRunner.processAckOnMain(backgroundStressorsCheckStage3, acks), StageResult.SUCCESS);

      Utils.sleep(1000);

      BackgroundStressorsStopStage backgroundStressorsStopStage1 = new BackgroundStressorsStopStage();
      BackgroundStressorsStopStage backgroundStressorsStopStage2 = new BackgroundStressorsStopStage();

      acks = new ArrayList<>(2);
      acks.add(stageRunner.executeOnWorker(backgroundStressorsStopStage1, 0));
      acks.add(stageRunner.executeOnWorker(backgroundStressorsStopStage2, 1));

      Assert.assertEquals(stageRunner.processAckOnMain(backgroundStressorsStopStage1, acks), StageResult.SUCCESS);

   }
}
