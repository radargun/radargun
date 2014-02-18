package org.radargun.stages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.stressors.BackgroundOpsManager;
import org.radargun.stressors.Statistics;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 1/4/13
 */
@Stage(doc = "Stop Statistics and return collected statistics to master.")
public class StopBackgroundStatisticsStage extends AbstractDistStage {

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
      Map<Integer, List<Statistics>> result = new HashMap<Integer, List<Statistics>>();
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dack = (DefaultDistStageAck) ack;
         result.put(dack.getSlaveIndex(), (List<Statistics>) dack.getPayload());
         if (dack.isError()) {
            return false;
         }
      }
      masterState.put(BackgroundOpsManager.NAME, result);
      return true;
   }
}
