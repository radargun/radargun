package org.radargun.stages;

import org.radargun.StageResult;
import org.radargun.util.CoreStageRunner;
import org.radargun.utils.TimeService;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class SleepStageTest {

   public void smokeTest() throws Exception {
      CoreStageRunner stageRunner = new CoreStageRunner(1);
      SleepStage sleepStage = new SleepStage();
      sleepStage.time = 1000;

      long start = TimeService.currentTimeMillis();
      StageResult stageResult = stageRunner.executeMainStage(sleepStage);
      long end = TimeService.currentTimeMillis();

      Assert.assertTrue(end - start >= 1000);
      Assert.assertEquals(stageResult, StageResult.SUCCESS);
   }
}
