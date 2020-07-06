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
import org.radargun.config.MainConfig;
import org.radargun.reporting.Report;
import org.radargun.reporting.Timeline;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.AbstractMainStage;
import org.radargun.state.MainState;
import org.radargun.state.WorkerState;
import org.radargun.traits.TraitHelper;

/**
 * @author Matej Cimbora
 */
public class CoreStageRunner {

   private final Cluster cluster;
   private final List<PerWorkerConfiguration> perWorkerConfigurations = new ArrayList<>(2);

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
         WorkerState workerState = new WorkerState();
         workerState.setWorkerIndex(i);
         workerState.setCluster(cluster);
         workerState.setPlugin(DEFAULT_PLUGIN);
         workerState.setService(DEFAULT_SERVICE);
         workerState.setTimeline(new Timeline(i));
         workerState.setTraits(traitMap);
         MainState mainState = new MainState(new MainConfig(DEFAULT_PORT, DEFAULT_HOST));
         mainState.setCluster(cluster);
         mainState.setReport(new Report(new Configuration(DEFAULT_CONFIGURATION), cluster));
         perWorkerConfigurations.add(new PerWorkerConfiguration(traitMap, workerState, mainState));
      }
   }

   public DistStageAck executeOnWorker(AbstractDistStage stage) throws Exception {
      return executeOnWorker(stage, 0);
   }

   public DistStageAck executeOnWorker(AbstractDistStage stage, int workerIndex) throws Exception {
      checkWorkerIndex(workerIndex);
      PerWorkerConfiguration perWorkerConfiguration = perWorkerConfigurations.get(workerIndex);
      TraitHelper.inject(stage, perWorkerConfiguration.traitMap);
      InitHelper.init(stage);
      stage.initOnWorker(perWorkerConfiguration.workerState);
      // TODO move elsewhere?
      stage.initOnMain(perWorkerConfiguration.mainState);
      return stage.executeOnWorker();
   }

   public List<DistStageAck> executeOnWorker(AbstractDistStage[] stages, int[] workerIndices) throws Exception {
      ExecutorService executor = Executors.newFixedThreadPool(stages.length);
      List<Callable<DistStageAck>> callables = new ArrayList<>(stages.length);
      IntStream.range(0, stages.length).forEach(i -> {
         callables.add(() -> executeOnWorker(stages[i], workerIndices[i]));
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

   public StageResult processAckOnMain(AbstractDistStage stage, List<DistStageAck> acks) {
      return stage.processAckOnMain(acks);
   }

   public StageResult executeMainStage(AbstractMainStage stage) throws Exception {
      // TODO more initialization
      InitHelper.init(stage);
      MainState mainState = new MainState(new MainConfig(DEFAULT_PORT, DEFAULT_HOST));
      mainState.setCluster(cluster);
      mainState.setReport(new Report(new Configuration(DEFAULT_CONFIGURATION), cluster));
      stage.init(mainState);
      return stage.execute();
   }

   protected Map<Class<?>, Object> getDefaultTraitMap() {
      return CoreTraitRepository.getAllTraits();
   }

   public <T> T getTraitImpl(Class<T> clazz) {
      return getTraitImpl(clazz, 0);
   }

   public <T> T getTraitImpl(Class<T> clazz, int workerIndex) {
      checkWorkerIndex(workerIndex);
      return (T) perWorkerConfigurations.get(workerIndex).traitMap.get(clazz);
   }

   public void replaceTraitImpl(Class clazz, Object traitImpl) {
      replaceTraitImpl(clazz, 0);
   }

   public void replaceTraitImpl(Class clazz, Object traitImpl, int workerIndex) {
      checkWorkerIndex(workerIndex);
      if (!perWorkerConfigurations.get(workerIndex).traitMap.containsKey(clazz)) {
         throw new IllegalArgumentException("Trait implementation for class " + clazz + " not found");
      }
      perWorkerConfigurations.get(workerIndex).traitMap.put(clazz, traitImpl);
   }

   public WorkerState getWorkerState() {
      return getWorkerState(0);
   }

   public WorkerState getWorkerState(int workerIndex) {
      checkWorkerIndex(workerIndex);
      return perWorkerConfigurations.get(workerIndex).workerState;
   }

   private static class PerWorkerConfiguration {
      private final Map<Class<?>, Object> traitMap;
      private final WorkerState workerState;
      private final MainState mainState;

      public PerWorkerConfiguration(Map<Class<?>, Object> traitMap, WorkerState workerState, MainState mainState) {
         this.traitMap = traitMap;
         this.workerState = workerState;
         this.mainState = mainState;
      }
   }

   private void checkWorkerIndex(int workerIndex) {
      if (workerIndex >= cluster.getSize()) {
         throw new IllegalArgumentException("Illegal worker index provided, expected value from range (0 - " + (cluster.getSize() - 1) + "), was " + workerIndex);
      }
   }
}
