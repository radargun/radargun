package org.cachebench.state;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.DistStage;
import org.cachebench.DistStageAck;
import org.cachebench.MasterStage;
import org.cachebench.Stage;
import org.cachebench.config.FixedSizeBenchmarkConfig;
import org.cachebench.config.ScalingBenchmarkConfig;
import org.cachebench.config.MasterConfig;

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
   private List<Stage> stages;
   private List<FixedSizeBenchmarkConfig> benchmarks;
   private FixedSizeBenchmarkConfig currentBenchmark;
   private DistStage currentDistStage;

   public MasterState(MasterConfig config) {
      this.config = config;
      benchmarks = new ArrayList<FixedSizeBenchmarkConfig>(config.getBenchmarks());
      if (benchmarks.isEmpty())
         throw new IllegalStateException("there must be at least one benchmark");
      moveToNextBenchmark();
   }

   public MasterConfig getConfig() {
      return config;
   }

   public DistStage getNextDistStageToProcess() {
      if (stages.isEmpty()) {
         if (!moveToNextBenchmark()) {
            return null;
         }
      }

      Stage toProcess;
      while ((toProcess = stages.remove(0)) instanceof MasterStage) {
         MasterStage servStage = (MasterStage) toProcess;
         executeServerStage(servStage);
         if (stages.isEmpty()) {
            if (!moveToNextBenchmark()) {
               return null;
            }
         }
      }

      //toProcess is a non null DistStage
      currentDistStage = (DistStage) toProcess;
      currentDistStage.initOnMaster(this, config.getSlaveCount());
      log.info("Starting dist stage '" + currentDistStage.getClass().getSimpleName() + "' on " + currentDistStage.getActiveSlaveCount() + " Slaves: " + currentDistStage);
      return currentDistStage;
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
      return currentDistStage.processAckOnMaster(acks);
   }

   private boolean moveToNextBenchmark() {
      if (benchmarks.isEmpty()) {
         log.info("Successfully executed all benchmarks, exiting.");
         return false;
      }
      currentBenchmark = benchmarks.remove(0);
      if (currentBenchmark instanceof ScalingBenchmarkConfig) {
         log.info("Started scaling benchmark '" + currentBenchmark.getName() + '\'');
      } else {
         log.info("Starting fixed size benchmark '" + currentBenchmark.getName() + '\'');
      }
      stages = currentBenchmark.getAssmbledStages();
      if (stages.isEmpty())
         throw new IllegalStateException("each benchmark must contain at least one stage");
      return true;
   }

   private void executeServerStage(MasterStage servStage) {
      log.info("Starting master stage '" + servStage.getClass().getSimpleName() + "' :" + servStage);
      servStage.init(this);
      if (!servStage.execute()) {
         log.warn("Issues while executing master stage: " + servStage);
      } else {
         log.trace("Master stage executed successfully " + servStage);
      }
   }

   public String nameOfTheCurrentBenchmark() {
      return currentBenchmark.getName();
   }
}
