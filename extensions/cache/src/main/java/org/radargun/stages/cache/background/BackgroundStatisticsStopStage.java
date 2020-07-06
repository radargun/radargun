package org.radargun.stages.cache.background;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.WorkerState;
import org.radargun.utils.Table;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Stop Statistics and return collected statistics to main.")
public class BackgroundStatisticsStopStage extends AbstractDistStage {
   @Property(doc = "Name of the background operations. Default is '" + BackgroundOpsManager.DEFAULT + "'.")
   protected String name = BackgroundOpsManager.DEFAULT;

   @Property(doc = "Name of the test used for reports. Default is 'BackgroundStats'.")
   private String testName = "BackgroundStats";

   @Override
   public DistStageAck executeOnWorker() {
      try {
         BackgroundStatisticsManager instance = BackgroundStatisticsManager.getInstance(workerState, name);
         if (instance != null) {
            instance.stopStats();
            return new StatisticsAck(workerState, instance.getStats());
         } else {
            return errorResponse("No background statistics " + name);
         }
      } catch (Exception e) {
         return errorResponse("Error while stopping background statistics", e);
      }
   }

   @Override
   public StageResult processAckOnMain(List<DistStageAck> acks) {
      StageResult res = super.processAckOnMain(acks);
      if (res.isError()) return res;
      if (testName == null || testName.isEmpty()) {
         log.warn("No test name - results are not recorded");
         return StageResult.SUCCESS;
      } else if (testName.equalsIgnoreCase("warmup")) {
         log.info("This test was executed as a warmup");
         return StageResult.SUCCESS;
      }
      Report report = mainState.getReport();
      Report.Test test = report.createTest(testName, null, false);
      Table<Integer, Integer, Long> cacheSizes = new Table<Integer, Integer, Long>();
      for (StatisticsAck ack : instancesOf(acks, StatisticsAck.class)) {
         int i = 0;
         for (BackgroundStatisticsManager.IterationStats stats : ack.iterations) {
            test.addStatistics(i, ack.getWorkerIndex(), stats.statistics);
            cacheSizes.put(ack.getWorkerIndex(), i, stats.cacheSize);
            ++i;
         }
      }
      for (int iteration : cacheSizes.columnKeys()) {
         Map<Integer, Report.WorkerResult> workerResults = new HashMap<Integer, Report.WorkerResult>();
         long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
         for (Map.Entry<Integer, Long> iterationData : cacheSizes.getColumn(iteration).entrySet()) {
            workerResults.put(iterationData.getKey(), new Report.WorkerResult(String.valueOf(iterationData.getValue()), false));
            // iterationData could be null if the worker is down
            if (iterationData != null) {
               min = Math.min(min, iterationData.getValue());
               max = Math.max(max, iterationData.getValue());
            }
         }
         Report.TestResult result = new Report.TestResult(BackgroundStatisticsManager.CACHE_SIZE, workerResults, min < max ? String.format("%d .. %d", min, max) : "-", false);
         test.addResult(iteration, result);
      }
      return StageResult.SUCCESS;
   }

   private static class StatisticsAck extends DistStageAck {
      public final List<BackgroundStatisticsManager.IterationStats> iterations;

      private StatisticsAck(WorkerState workerState, List<BackgroundStatisticsManager.IterationStats> iterations) {
         super(workerState);
         this.iterations = iterations;
      }
   }
}
