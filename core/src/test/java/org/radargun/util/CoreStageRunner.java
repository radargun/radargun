package org.radargun.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.InitHelper;
import org.radargun.config.MasterConfig;
import org.radargun.reporting.Report;
import org.radargun.reporting.Timeline;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.AbstractMasterStage;
import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;
import org.radargun.traits.TraitHelper;

/**
 * @author Matej Cimbora
 */
public class CoreStageRunner {

   private final Cluster cluster;
   private final List<PerSlaveConfiguration> perSlaveConfigurations = new ArrayList<>(2);

   private static final String DEFAULT_PLUGIN = "plugin";
   private static final String DEFAULT_SERVICE = "service";
   private static final String DEFAULT_HOST = "localhost";
   private static final String DEFAULT_CONFIGURATION = "configuration";

   private static final int DEFAULT_PORT = 2103;

   public CoreStageRunner(int clusterSize) {
      if (clusterSize <= 0) {
         throw new IllegalArgumentException("Cluster size needs to be greater than 0, was " + clusterSize);
      }
      Cluster cluster = new Cluster();
      cluster.setSize(clusterSize);
      this.cluster = cluster;
      for (int i = 0; i < clusterSize; i++) {
         Map<Class<?>, Object> traitMap = getDefaultTraitMap();
         SlaveState slaveState = new SlaveState();
         slaveState.setSlaveIndex(i);
         slaveState.setCluster(cluster);
         slaveState.setPlugin(DEFAULT_PLUGIN);
         slaveState.setService(DEFAULT_SERVICE);
         slaveState.setTimeline(new Timeline(i));
         slaveState.setTraits(traitMap);
         MasterState masterState = new MasterState(new MasterConfig(DEFAULT_PORT, DEFAULT_HOST));
         masterState.setCluster(cluster);
         masterState.setReport(new Report(new Configuration(DEFAULT_CONFIGURATION), cluster));
         perSlaveConfigurations.add(new PerSlaveConfiguration(traitMap, slaveState, masterState));
      }
   }

   public DistStageAck executeOnSlave(AbstractDistStage stage) throws Exception {
      return executeOnSlave(stage, 0);
   }

   public DistStageAck executeOnSlave(AbstractDistStage stage, int slaveIndex) throws Exception {
      checkSlaveIndex(slaveIndex);
      PerSlaveConfiguration perSlaveConfiguration = perSlaveConfigurations.get(slaveIndex);
      TraitHelper.inject(stage, perSlaveConfiguration.traitMap);
      InitHelper.init(stage);
      stage.initOnSlave(perSlaveConfiguration.slaveState);
      // TODO move elsewhere?
      stage.initOnMaster(perSlaveConfiguration.masterState);
      return stage.executeOnSlave();
   }

   public List<DistStageAck> executeOnSlave(AbstractDistStage[] stages, int[] slaveIndices) throws Exception {
      ExecutorService executor = Executors.newFixedThreadPool(stages.length);
      List<Callable<DistStageAck>> callables = new ArrayList<>(stages.length);
      IntStream.range(0, stages.length).forEach(i -> {
         callables.add(() -> executeOnSlave(stages[i], slaveIndices[i]));
      });
      List<Future<DistStageAck>> futures = executor.invokeAll(callables);
      List<DistStageAck> acks = new ArrayList<>(stages.length);
      futures.stream().forEach(f -> {
         try {
            acks.add(f.get());
         } catch (InterruptedException e) {
            throw new IllegalStateException(e);
         } catch (ExecutionException e) {
            throw new IllegalStateException(e);
         }
      });
      return acks;
   }

   public StageResult processAckOnMaster(AbstractDistStage stage, List<DistStageAck> acks) {
      return stage.processAckOnMaster(acks);
   }

   public StageResult executeMasterStage(AbstractMasterStage stage) throws Exception {
      // TODO more initialization
      InitHelper.init(stage);
      MasterState masterState = new MasterState(new MasterConfig(DEFAULT_PORT, DEFAULT_HOST));
      masterState.setCluster(cluster);
      masterState.setReport(new Report(new Configuration(DEFAULT_CONFIGURATION), cluster));
      stage.init(masterState);
      return stage.execute();
   }

   protected Map<Class<?>, Object> getDefaultTraitMap() {
      return CoreTraitRepository.getAllTraits();
   }

   public <T> T getTraitImpl(Class<T> clazz) {
      return getTraitImpl(clazz, 0);
   }

   public <T> T getTraitImpl(Class<T> clazz, int slaveIndex) {
      checkSlaveIndex(slaveIndex);
      return (T) perSlaveConfigurations.get(slaveIndex).traitMap.get(clazz);
   }

   public void replaceTraitImpl(Class clazz, Object traitImpl) {
      replaceTraitImpl(clazz, 0);
   }

   public void replaceTraitImpl(Class clazz, Object traitImpl, int slaveIndex) {
      checkSlaveIndex(slaveIndex);
      if (!perSlaveConfigurations.get(slaveIndex).traitMap.containsKey(clazz)) {
         throw new IllegalArgumentException("Trait implementation for class " + clazz + " not found");
      }
      perSlaveConfigurations.get(slaveIndex).traitMap.put(clazz, traitImpl);
   }

   public SlaveState getSlaveState() {
      return getSlaveState(0);
   }

   public SlaveState getSlaveState(int slaveIndex) {
      checkSlaveIndex(slaveIndex);
      return perSlaveConfigurations.get(slaveIndex).slaveState;
   }

   private static class PerSlaveConfiguration {
      private final Map<Class<?>, Object> traitMap;
      private final SlaveState slaveState;
      private final MasterState masterState;

      public PerSlaveConfiguration(Map<Class<?>, Object> traitMap, SlaveState slaveState, MasterState masterState) {
         this.traitMap = traitMap;
         this.slaveState = slaveState;
         this.masterState = masterState;
      }
   }

   private void checkSlaveIndex(int slaveIndex) {
      if (slaveIndex >= cluster.getSize()) {
         throw new IllegalArgumentException("Illegal slave index provided, expected value from range (0 - " + (cluster.getSize() - 1) + "), was " + slaveIndex);
      }
   }
}
