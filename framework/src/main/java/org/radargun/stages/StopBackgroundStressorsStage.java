package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.stressors.BackgroundOpsManager;

/**
 * 
 * Stop BackgroundStressors.
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
@Stage(doc = "Stop BackgroundStressors.")
public class StopBackgroundStressorsStage extends AbstractDistStage {

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         BackgroundOpsManager instance = BackgroundOpsManager.getInstance(slaveState);
         if (instance != null) {
            instance.stopStressors();
         } else {
            log.error("No " + BackgroundOpsManager.NAME);
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
}
