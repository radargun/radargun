package org.radargun.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.DistStage;
import org.radargun.DistStageAck;
import org.radargun.MasterStage;
import org.radargun.Stage;
import org.radargun.config.AbstractBenchmarkConfig;
import org.radargun.config.MasterConfig;
import org.radargun.utils.Utils;

/**
 * State residing on the server, passed to each stage before execution.
 *
 * @author Mircea.Markus@jboss.com
 */
public class MasterState extends StateBase {

   private static Log log = LogFactory.getLog(MasterState.class);

   private MasterConfig config;
   private List<AbstractBenchmarkConfig> benchmarks;
   private AbstractBenchmarkConfig currentBenchmark;
   private long startTime = System.currentTimeMillis();
   private DistStage currentDistStage;

   public MasterState(MasterConfig config) {
      this.config = config;
      benchmarks = new ArrayList<AbstractBenchmarkConfig>(config.getBenchmarks());
      if (benchmarks.isEmpty())
         throw new IllegalStateException("There must be at least one benchmark");
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
            return currentDistStage;
         } else {
            executeServerStage((MasterStage) stage);
         }
      }
      //if we are here it means we finished executed the current benchmark and we should move to next one
      if (benchmarks.size() == 0) {
         long duration = System.currentTimeMillis() - startTime;
         String duartionStr = Utils.getMillisDurationString(duration);
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

   public int getSlavesCountForCurrentStage() {
      return currentDistStage.getActiveSlaveCount();
   }

   public boolean distStageFinished(List<DistStageAck> acks) {
      // Sort acks so that logs are more readable.
      Collections.sort(acks, new Comparator<DistStageAck>() {
         @Override
         public int compare(DistStageAck o1, DistStageAck o2) {
            int thisVal = o1.getSlaveIndex();
            int anotherVal = o2.getSlaveIndex();
            return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));
         }
      });
      boolean stageOk = currentDistStage.processAckOnMaster(acks, this);
      if (stageOk) return true;
      if (!currentDistStage.isExitBenchmarkOnSlaveFailure()) {
         log.warn("Execution error for current benchmark, skipping rest of the stages");
         currentBenchmark.errorOnCurrentBenchmark();
         return true;
      } else {
         log.info("Exception error on current stage, and exiting (stage's exitBenchmarkOnSlaveFailure is set to true).");
         return false;
      }
   }

   private void executeServerStage(MasterStage servStage) {
      if (log.isDebugEnabled())
         log.debug("Starting '" + servStage.getClass().getSimpleName() + "' on master node only. Details:" + servStage);
      else
         log.info("Starting '" + servStage.getClass().getSimpleName() + "' on master node only.");
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
         throw new IllegalStateException("Null product name not allowed!");
      }
      return prodName;
   }

   public String configNameOfTheCurrentBenchmark() {
      return currentBenchmark.getConfigName();
   }

   private void logBenchmarkStarted() {
      if (currentBenchmark.getProductName() != null) {
         log.info("Started benchmarking product '" + currentBenchmark.getProductName() + "' with configuration '" + currentBenchmark.getConfigName() + "'");
      }
   }
}
