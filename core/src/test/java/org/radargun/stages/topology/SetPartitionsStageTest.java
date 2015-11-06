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
      Set<Integer> slaveIds0 = new HashSet<>();
      slaveIds0.add(0);
      slaveIds0.add(1);
      partitions.add(slaveIds0);
      Set<Integer> slaveIds1 = new HashSet<>();
      slaveIds1.add(2);
      partitions.add(slaveIds1);
      Utils.setField(SetPartitionsStage.class, "partitions", setPartitionsStage, partitions);

      CoreTraitRepository.Partitionable partitionable = (CoreTraitRepository.Partitionable) stageRunner.getTraitImpl(Partitionable.class);
      Assert.assertEquals(partitionable.getSlaveIndex(), -1);
      Assert.assertEquals(partitionable.getPartitionMembers(), null);
      Assert.assertEquals(partitionable.getInitiallyReachable(), null);

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnSlave(setPartitionsStage, 0));

      Assert.assertEquals(partitionable.getSlaveIndex(), 0);
      Assert.assertEquals(partitionable.getPartitionMembers(), slaveIds0);
      Assert.assertEquals(partitionable.getInitiallyReachable(), null);
      Assert.assertEquals(stageRunner.processAckOnMaster(setPartitionsStage, acks), StageResult.SUCCESS);
   }
}
