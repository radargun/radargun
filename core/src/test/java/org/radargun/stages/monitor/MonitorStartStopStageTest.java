package org.radargun.stages.monitor;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.traits.Lifecycle;
import org.radargun.util.CoreStageRunner;
import org.radargun.utils.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class MonitorStartStopStageTest {

   public void smokeTest() throws Exception {
      CoreStageRunner stageRunner = new CoreStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      MonitorStartStage monitorStartStage = new MonitorStartStage();

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnWorker(monitorStartStage, 0));

      Assert.assertEquals(stageRunner.processAckOnMain(monitorStartStage, acks), StageResult.SUCCESS);

      Utils.sleep(3000);

      MonitorStopStage monitorStopStage = new MonitorStopStage();

      acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnWorker(monitorStopStage, 0));

      Assert.assertEquals(stageRunner.processAckOnMain(monitorStopStage, acks), StageResult.SUCCESS);

   }
}
