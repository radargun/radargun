package org.radargun.stages.topology;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.TopologyHistory;
import org.radargun.util.CacheStageRunner;
import org.radargun.util.CacheTraitRepository;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class WaitForTopologyEventTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      WaitForTopologyEventStage waitForTopologyEventStage = new WaitForTopologyEventStage();
      waitForTopologyEventStage.timeout = 10000;

      CacheTraitRepository.TopologyHistory topologyHistory = (CacheTraitRepository.TopologyHistory) stageRunner.getTraitImpl(TopologyHistory.class);
      Assert.assertTrue(topologyHistory.getTopologyChangeHistory(null).isEmpty());
      Assert.assertTrue(topologyHistory.getRehashHistory(null).isEmpty());
      Assert.assertTrue(topologyHistory.getCacheStatusChangeHistory(null).isEmpty());

      topologyHistory.triggerHistoryChanges(3000, 1000, TimeUnit.MILLISECONDS);

      List<DistStageAck> acks = new ArrayList<>(1);

      Thread stageThread = new Thread(() -> {
         try {
            acks.add(stageRunner.executeOnSlave(waitForTopologyEventStage, 0));
         } catch (Exception e) {
            throw new IllegalStateException(e);
         }
      });
      stageThread.start();
      stageThread.join(10000);
      topologyHistory.stopHistoryChanges();

      Assert.assertFalse(topologyHistory.getTopologyChangeHistory(null).isEmpty());
      Assert.assertFalse(topologyHistory.getRehashHistory(null).isEmpty());
      Assert.assertFalse(topologyHistory.getCacheStatusChangeHistory(null).isEmpty());
      Assert.assertEquals(stageRunner.processAckOnMaster(waitForTopologyEventStage, acks), StageResult.SUCCESS);
   }
}
