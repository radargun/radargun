package org.radargun.stages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.DistStageAck;
import org.radargun.state.MasterState;
import org.radargun.stressors.BackgroundStats;
import org.radargun.stressors.BackgroundStats.Stats;

/**
 * 
 * Stop BackgroundStats and return collected statistics to master.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 */
public class StopBackgroundStatsStage extends AbstractDistStage {

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         BackgroundStats bgStats = (BackgroundStats) slaveState.get(BackgroundStats.NAME);
         if (bgStats != null) {
            ack.setPayload(bgStats.stopStats());
            bgStats.stopStressors();
            slaveState.remove(BackgroundStats.NAME);
         } else {
            log.error("BackgroundStats not available");
            ack.setError(true);
         }
         return ack;
      } catch (Exception e) {
         log.error("Error while stopping background stats", e);
         ack.setError(true);
         ack.setRemoteException(e);
         return ack;
      }
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      Map<Integer, List<Stats>> result = new HashMap<Integer, List<Stats>>();
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dack = (DefaultDistStageAck) ack;
         result.put(dack.getSlaveIndex(), (List<Stats>) dack.getPayload());
         if (dack.isError()) {
            return false;
         }
      }
      masterState.put(BackgroundStats.NAME, result);
      return true;
   }

   @Override
   public String toString() {
      return "StopBackgroundStatsStage {" + super.toString();
   }
}
