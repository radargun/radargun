package org.radargun.stages.cache.background;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.DefaultDistStageAck;

/**
 * 
 * Stop BackgroundStressors.
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
@Stage(doc = "Stop BackgroundStressors.")
public class BackgroundStressorsStopStage extends AbstractDistStage {

   @Property(doc = "If true, the phase does not finish until all stressors stop loading its data. Default is false.")
   private boolean waitUntilLoaded = false;

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         BackgroundOpsManager instance = BackgroundOpsManager.getInstance(slaveState);
         if (instance != null) {
            instance.waitUntilLoaded();
            instance.stopBackgroundThreads();
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
