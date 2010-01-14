package org.cachebench.state;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.DistStage;
import org.cachebench.DistStageAck;
import org.cachebench.MasterStage;
import org.cachebench.Stage;
import org.cachebench.config.FixedSizeBenchmarkConfig;
import org.cachebench.config.MasterConfig;
import org.cachebench.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * State residing on the server, passed to each stage before execution.
 *
 * @author Mircea.Markus@jboss.com
 */
public class MasterState extends StateBase {

   private static Log log = LogFactory.getLog(MasterState.class);

   private MasterConfig config;
   private List<FixedSizeBenchmarkConfig> benchmarks;
   private FixedSizeBenchmarkConfig currentBenchmark;
   private long startTime = System.currentTimeMillis();
   private DistStage currentDistStage;

   private boolean stopOnError = false;

   public MasterState(MasterConfig config) {
      this.config = config;
      benchmarks = new ArrayList<FixedSizeBenchmarkConfig>(config.getBenchmarks());
      if (benchmarks.isEmpty())
         throw new IllegalStateException("there must be at least one benchmark");
      currentBenchmark = benchmarks.remove(0);
      logBenchmarkStarted();
   }

   public MasterConfig getConfig() {
      return config;
   }

   public DistStage getNextDistStageToProcess() {
      while (currentBenchmark.hasNextStage()) {
         Stage stage = currentBenchmark.nextStage();
         if (stage instanceof DistStage) {
            currentDistStage = (DistStage) stage;
            currentDistStage.initOnMaster(this, config.getSlaveCount());
            log.info("Starting dist stage '" + currentDistStage.getClass().getSimpleName() + "' on " + currentDistStage.getActiveSlaveCount() + " Slaves: " + currentDistStage);
            return currentDistStage;
         } else {
            executeServerStage((MasterStage) stage);
         }
      }
      //if we are here it means we finished executed the current benchmark and we should move to next one
      if (benchmarks.size() == 0) {
         long duration = System.currentTimeMillis() - startTime;
         String duartionStr = Utils.getDurationString(duration);
         log.info("Successfully executed all benchmarks in " + duartionStr + ", exiting.");
         return null;
      }
      currentBenchmark = benchmarks.remove(0);
      logBenchmarkStarted();
      return getNextDistStageToProcess();
   }


   public DistStage getCurrentDistStage() {
      return currentDistStage;
   }

   public Set<Integer> getSlaveIndexesForCurrentStage() {
      Set<Integer> result = new TreeSet<Integer>();
      for (int i = 0; i < currentDistStage.getActiveSlaveCount(); i++) {
         result.add(i);
      }
      return result;
   }

   public int getSlavesCountForCurrentStage() {
      return currentDistStage.getActiveSlaveCount();
   }

   public boolean distStageFinished(List<DistStageAck> acks) {
      boolean stageOk = currentDistStage.processAckOnMaster(acks);
      if (stageOk) return true;
      if (!stopOnError) {
         log.warn("Execution error for current benchmark, skipping rest of the stages");
         currentBenchmark.errorOnCurentBenchmark();
         return true;
      } else {
         return false;
      }
   }

   private void executeServerStage(MasterStage servStage) {
      log.info("Starting master stage '" + servStage.getClass().getSimpleName() + "' :" + servStage);
      servStage.init(this);
      try {
         if (!servStage.execute()) {
            log.warn("Issues while executing master stage: " + servStage);
         } else {
            log.trace("Master stage executed successfully " + servStage);
         }
      } catch (Exception e) {
         log.warn("Caught exception", e);
      }
   }

   public String nameOfTheCurrentBenchmark() {
      String prodName = currentBenchmark.getProductName();
      if (prodName == null) {
         throw new IllegalStateException("Null prod name not allowed!");
      }
      return prodName;
   }

   public String configNameOfTheCurrentBenchmark() {
      return currentBenchmark.getConfigName();
   }

   private void logBenchmarkStarted() {
      log.info("Started benchmark '" + currentBenchmark.getProductName() + '\'');
   }
}
