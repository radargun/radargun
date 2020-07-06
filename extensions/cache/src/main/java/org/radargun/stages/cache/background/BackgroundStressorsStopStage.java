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
   @Property(doc = "Name of the background operations. Default is '" + BackgroundOpsManager.DEFAULT + "'.")
   protected String name = BackgroundOpsManager.DEFAULT;

   @Override
   public DistStageAck executeOnWorker() {
      try {
         BackgroundOpsManager instance = BackgroundOpsManager.getInstance(workerState, name);
         if (instance != null) {
            instance.waitUntilLoaded();
            instance.stopBackgroundThreads();
         } else {
            return errorResponse("No background stressors " + name);
         }
         return successfulResponse();
      } catch (Exception e) {
         return errorResponse("Error while stopping background stats", e);
      }
   }
}
