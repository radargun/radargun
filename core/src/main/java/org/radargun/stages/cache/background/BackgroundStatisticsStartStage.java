package org.radargun.stages.cache.background;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stages.AbstractDistStage;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Starts collection of statistics from background threads and cache size.")
public class BackgroundStatisticsStartStage extends AbstractDistStage {
   @Property(converter = TimeConverter.class, doc = "Delay between statistics snapshots. Default is 5 seconds.")
   private long statsIterationDuration = 5000;

   @Override
   public DistStageAck executeOnSlave() {
      try {
         BackgroundOpsManager instance = BackgroundOpsManager.getOrCreateInstance(slaveState, statsIterationDuration);

         log.info("Starting statistics threads");
         instance.startStats();

         return successfulResponse();
      } catch (Exception e) {
         return errorResponse("Error while starting background stats", e);
      }
   }
}
