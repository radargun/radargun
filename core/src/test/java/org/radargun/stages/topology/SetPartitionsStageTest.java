package org.radargun.stages.topology;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.Partitionable;
import org.radargun.util.CoreStageRunner;
import org.radargun.util.CoreTraitRepository;
import org.radargun.utils.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class SetPartitionsStageTest {

   public void smokeTest() throws Exception {
      CoreStageRunner stageRunner = new CoreStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      SetPartitionsStage setPartitionsStage = new SetPartitionsStage();
      List<Set<Integer>> partitions = new ArrayList<>(1);
      Set<Integer> workerIds0 = new HashSet<>();
      workerIds0.add(0);
      workerIds0.add(1);
      partitions.add(workerIds0);
      Set<Integer> workerIds1 = new HashSet<>();
      workerIds1.add(2);
      partitions.add(workerIds1);
      Utils.setField(SetPartitionsStage.class, "partitions", setPartitionsStage, partitions);

      CoreTraitRepository.Partitionable partitionable = (CoreTraitRepository.Partitionable) stageRunner.getTraitImpl(Partitionable.class);
      Assert.assertEquals(partitionable.getWorkerIndex(), -1);
      Assert.assertEquals(partitionable.getPartitionMembers(), null);
      Assert.assertEquals(partitionable.getInitiallyReachable(), null);

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnWorker(setPartitionsStage, 0));

      Assert.assertEquals(partitionable.getWorkerIndex(), 0);
      Assert.assertEquals(partitionable.getPartitionMembers(), workerIds0);
      Assert.assertEquals(partitionable.getInitiallyReachable(), null);
      Assert.assertEquals(stageRunner.processAckOnMain(setPartitionsStage, acks), StageResult.SUCCESS);
   }
}
