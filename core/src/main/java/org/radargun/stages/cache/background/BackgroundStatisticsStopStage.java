package org.radargun.stages.cache.background;

import static org.radargun.utils.Utils.cast;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.stats.Statistics;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Stop Statistics and return collected statistics to master.")
public class BackgroundStatisticsStopStage extends AbstractDistStage {

   @Property(doc = "Name of the test used for reports. Default is 'BackgroundStats'.")
   private String testName = "BackgroundStats";

   @Override
   public DistStageAck executeOnSlave() {
      try {
         BackgroundOpsManager instance = BackgroundOpsManager.getInstance(slaveState);
         if (instance != null) {
            return new StatisticsAck(slaveState, instance.stopStats());
         } else {
            return errorResponse("No " + BackgroundOpsManager.NAME);
         }
      } catch (Exception e) {
         return errorResponse("Error while stopping background statistics", e);
      }
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      if (!super.processAckOnMaster(acks)) return false;
      Report report = masterState.getReport();
      Report.Test test = report.createTest(testName);
      for (StatisticsAck ack : cast(acks, StatisticsAck.class)) {
         test.addIterations(ack.getSlaveIndex(), ack.iterations);
      }
      return true;
   }

   private static class StatisticsAck extends DistStageAck {
      final List<List<Statistics>> iterations;

      private StatisticsAck(SlaveState slaveState, List<List<Statistics>> iterations) {
         super(slaveState);
         this.iterations = iterations;
      }
   }
}
