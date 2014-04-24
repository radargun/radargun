package org.radargun.stages.cache.background;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.DefaultDistStageAck;
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
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         BackgroundOpsManager instance = BackgroundOpsManager.getInstance(slaveState);
         if (instance != null) {
            ack.setPayload(instance.stopStats());
         } else {
            log.error("No " + BackgroundOpsManager.NAME);
            ack.setError(true);
         }
         return ack;
      } catch (Exception e) {
         log.error("Error while stopping background statistics", e);
         ack.setError(true);
         ack.setRemoteException(e);
         return ack;
      }
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      Report report = masterState.getReport();
      Report.Test test = report.createTest(testName);
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dack = (DefaultDistStageAck) ack;
         test.addIterations(dack.getSlaveIndex(), (List<List<Statistics>>) dack.getPayload());
         if (dack.isError()) {
            return false;
         }
      }
      return true;
   }
}
