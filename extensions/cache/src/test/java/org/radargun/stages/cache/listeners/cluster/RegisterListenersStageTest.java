package org.radargun.stages.cache.listeners.cluster;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.state.WorkerState;
import org.radargun.traits.Lifecycle;
import org.radargun.util.CacheStageRunner;
import org.radargun.util.CacheTraitRepository;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class RegisterListenersStageTest {

   public void smokeTest() throws Exception {
      CacheStageRunner stageRunner = new CacheStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      RegisterListenersStage registerListenersStage = new RegisterListenersStage();
      registerListenersStage.registerListeners = true;

      WorkerState workerState = stageRunner.getWorkerState();
      Assert.assertNull(workerState.get(CacheTraitRepository.CacheListeners.CREATED.name));
      Assert.assertNull(workerState.get(CacheTraitRepository.CacheListeners.UPDATED.name));
      Assert.assertNull(workerState.get(CacheTraitRepository.CacheListeners.REMOVED.name));
      Assert.assertNull(workerState.get(CacheTraitRepository.CacheListeners.EVICTED.name));
      Assert.assertNull(workerState.get(CacheTraitRepository.CacheListeners.EXPIRED.name));

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnWorker(registerListenersStage, 0));

      Assert.assertNotNull(workerState.get(CacheTraitRepository.CacheListeners.CREATED.name));
      Assert.assertNotNull(workerState.get(CacheTraitRepository.CacheListeners.UPDATED.name));
      Assert.assertNotNull(workerState.get(CacheTraitRepository.CacheListeners.REMOVED.name));
      Assert.assertNotNull(workerState.get(CacheTraitRepository.CacheListeners.EVICTED.name));
      Assert.assertNotNull(workerState.get(CacheTraitRepository.CacheListeners.EXPIRED.name));
      Assert.assertEquals(stageRunner.processAckOnMain(registerListenersStage, acks), StageResult.SUCCESS);

      registerListenersStage.registerListeners = false;
      registerListenersStage.unregisterListeners = true;

      acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnWorker(registerListenersStage, 0));

      Assert.assertNull(workerState.get(CacheTraitRepository.CacheListeners.CREATED.name));
      Assert.assertNull(workerState.get(CacheTraitRepository.CacheListeners.UPDATED.name));
      Assert.assertNull(workerState.get(CacheTraitRepository.CacheListeners.REMOVED.name));
      Assert.assertNull(workerState.get(CacheTraitRepository.CacheListeners.EVICTED.name));
      Assert.assertNull(workerState.get(CacheTraitRepository.CacheListeners.EXPIRED.name));
      Assert.assertEquals(stageRunner.processAckOnMain(registerListenersStage, acks), StageResult.SUCCESS);

   }
}
