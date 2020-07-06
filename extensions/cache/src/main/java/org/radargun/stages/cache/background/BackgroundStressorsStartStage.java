package org.radargun.stages.cache.background;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.PropertyDelegate;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.helpers.CacheSelector;

/**
 *
 * Create BackgroundStressors and store them to WorkerState. Optionally start stressor or stat threads.
 *
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Starts background stressor threads.")
public class BackgroundStressorsStartStage extends AbstractDistStage {

   @Property(doc = "Name of the background operations. Default is '" + BackgroundOpsManager.DEFAULT + "'.")
   protected String name = BackgroundOpsManager.DEFAULT;

   @PropertyDelegate
   protected GeneralConfiguration generalConfiguration = new GeneralConfiguration();

   @PropertyDelegate
   protected BackgroundStressorLogicConfiguration backgroundStressorLogicConfiguration = new BackgroundStressorLogicConfiguration();

   @PropertyDelegate(prefix = "logLogic.")
   protected LogLogicConfiguration logLogicConfiguration = new LogLogicConfiguration();

   @Override
   public DistStageAck executeOnWorker() {
      validateConfiguration();
      workerState.put(CacheSelector.CACHE_SELECTOR, new CacheSelector.UseCache(generalConfiguration.cacheName));
      try {
         BackgroundOpsManager instance = BackgroundOpsManager.getOrCreateInstance(workerState, name,
            generalConfiguration, backgroundStressorLogicConfiguration, logLogicConfiguration);

         log.info("Starting stressor threads " + name);
         if (isServiceRunning()) {
            instance.startBackgroundThreads();
         }

         return successfulResponse();
      } catch (Exception e) {
         return errorResponse("Error while starting background threads.", e);
      }
   }

   private void validateConfiguration() {
      if (generalConfiguration.numEntries < generalConfiguration.numThreads * workerState.getGroupSize()) {
         throw new IllegalArgumentException(String.format("'numEntries' needs to be greater than or equal to the product" +
               " of 'numThreads' and group size'. Required minimum '%d', was: '%d'.", generalConfiguration.numEntries,
            generalConfiguration.numThreads * workerState.getGroupSize()));
      }
   }
}
