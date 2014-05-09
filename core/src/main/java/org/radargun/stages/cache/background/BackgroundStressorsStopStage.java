package org.radargun.stages.cache.background;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;

/**
 * 
 * Stop BackgroundStressors.
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
@Stage(doc = "Stop BackgroundStressors.")
public class BackgroundStressorsStopStage extends AbstractDistStage {

   @Property(doc = "If true, the phase does not finish until all stressors stop loading its data. Default is false.")
   private boolean legacyWaitUntilLoaded = false;

   @Override
   public DistStageAck executeOnSlave() {
      try {
         BackgroundOpsManager instance = BackgroundOpsManager.getInstance(slaveState);
         if (instance != null) {
            instance.waitUntilLoaded();
            instance.stopBackgroundThreads();
         } else {
            return errorResponse("No " + BackgroundOpsManager.NAME);
         }
         return successfulResponse();
      } catch (Exception e) {
         return errorResponse("Error while stopping background stats", e);
      }
   }
}
