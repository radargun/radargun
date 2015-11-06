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
import org.radargun.utils.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class WaitForTopologySettleTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      WaitForTopologySettleStage waitForTopologySettleStage = new WaitForTopologySettleStage();
      waitForTopologySettleStage.period = 3000;

      CacheTraitRepository.TopologyHistory topologyHistory = (CacheTraitRepository.TopologyHistory) stageRunner.getTraitImpl(TopologyHistory.class);
      Assert.assertTrue(topologyHistory.getTopologyChangeHistory(null).isEmpty());
      Assert.assertTrue(topologyHistory.getRehashHistory(null).isEmpty());
      Assert.assertTrue(topologyHistory.getCacheStatusChangeHistory(null).isEmpty());

      topologyHistory.triggerHistoryChanges(0, 1000, TimeUnit.MILLISECONDS);

      Utils.sleep(1000);

      List<DistStageAck> acks = new ArrayList<>(1);

      Thread stageThread = new Thread(() -> {
         try {
            acks.add(stageRunner.executeOnSlave(waitForTopologySettleStage, 0));
         } catch (Exception e) {
            throw new IllegalStateException(e);
         }
      });
      stageThread.start();

      topologyHistory.stopHistoryChanges();
      stageThread.join(10000);

      Assert.assertFalse(topologyHistory.getTopologyChangeHistory(null).isEmpty());
      Assert.assertFalse(topologyHistory.getRehashHistory(null).isEmpty());
      Assert.assertFalse(topologyHistory.getCacheStatusChangeHistory(null).isEmpty());
      Assert.assertEquals(stageRunner.processAckOnMaster(waitForTopologySettleStage, acks), StageResult.SUCCESS);
   }
}
