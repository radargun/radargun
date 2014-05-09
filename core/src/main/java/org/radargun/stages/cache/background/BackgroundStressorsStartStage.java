package org.radargun.stages.cache.background;

import org.radargun.DistStageAck;
import org.radargun.config.PropertyDelegate;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.helpers.BucketPolicy;

/**
 * 
 * Create BackgroundStressors and store them to SlaveState. Optionally start stressor or stat threads.
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Starts background stressor threads.")
public class BackgroundStressorsStartStage extends AbstractDistStage {

   @PropertyDelegate(prefix = "")
   protected GeneralConfiguration generalConfiguration = new GeneralConfiguration();

   @PropertyDelegate(prefix = "legacy.")
   protected LegacyLogicConfiguration legacyLogicConfiguration = new LegacyLogicConfiguration();

   @PropertyDelegate(prefix = "logLogic.")
   protected LogLogicConfiguration logLogicConfiguration = new LogLogicConfiguration();

   @Override
   public DistStageAck executeOnSlave() {
      slaveState.put(BucketPolicy.LAST_BUCKET, generalConfiguration.cacheName);
      try {
         BackgroundOpsManager instance = BackgroundOpsManager.getOrCreateInstance(slaveState,
               generalConfiguration, legacyLogicConfiguration, logLogicConfiguration);

         log.info("Starting stressor threads");
         if (isServiceRunnning()) {
            instance.startBackgroundThreads();
         }

         return successfulResponse();
      } catch (Exception e) {
         return errorResponse("Error while starting background stats", e);
      }
   }
}
