package org.radargun.stages.cache.background;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.utils.TimeConverter;

@Stage(doc = "Starts collection of statistics from background threads and cache size.")
public class BackgroundStatisticsStartStage extends AbstractDistStage {
   @Property(doc = "Name of the background operations. Default is '" + BackgroundOpsManager.DEFAULT + "'.")
   protected String name = BackgroundOpsManager.DEFAULT;

   @Property(converter = TimeConverter.class, doc = "Delay between statistics snapshots. Default is 5 seconds.")
   private long statsIterationDuration = 5000;

   @Override
   public DistStageAck executeOnSlave() {
      try {
         BackgroundStatisticsManager instance = BackgroundStatisticsManager.getOrCreateInstance(slaveState, name, statsIterationDuration);

         log.info("Starting statistics threads");
         instance.startStats();

         return successfulResponse();
      } catch (Exception e) {
         return errorResponse("Error while starting background stats", e);
      }
   }
}
