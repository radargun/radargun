package org.radargun.stages.cache.background;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.DefaultDistStageAck;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Starts collection of statistics from background threads and cache size.")
public class BackgroundStatisticsStartStage extends AbstractDistStage {
   @Property(converter = TimeConverter.class, doc = "Delay between statistics snapshots. Default is 5 seconds.")
   private long statsIterationDuration = 5000;

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         BackgroundOpsManager instance = BackgroundOpsManager.getOrCreateInstance(slaveState, statsIterationDuration);

         log.info("Starting statistics threads");
            instance.startStats();

         return ack;
      } catch (Exception e) {
         log.error("Error while starting background stats", e);
         ack.setError(true);
         ack.setRemoteException(e);
         return ack;
      }
   }
}
