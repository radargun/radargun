package org.radargun.stages.cache.background;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
@Stage(doc = "Stops data loading process started by BackgroundLoadDataStartStage.")
public class BackgroundCacheLoadStopStage extends AbstractDistStage {

   private static final String BACKGROUND_LOADERS = "BackgroundLoaders";

   @Property(converter = TimeConverter.class, doc = "Maximum time to wait for loading threads to finish. By default, " +
         "wait until the threads finish their job.")
   private long timeoutDuration = 0;

   @Override
   public DistStageAck executeOnSlave() {
      List<Thread> loaders = (List<Thread>) slaveState.get(BACKGROUND_LOADERS);
      if (loaders != null) {
         long remaining = timeoutDuration;
         for (Thread loader : loaders) {
            try {
               if (timeoutDuration > 0) {
                  if (remaining > 0) {
                     long start = TimeService.currentTimeMillis();
                     loader.join(remaining);
                     remaining -= TimeService.currentTimeMillis() - start;
                  } else {
                     return errorResponse(String.format("Timed out after waiting for loading threads to finish." +
                           " Timeout duration was %s.", Utils.prettyPrintTime(timeoutDuration, TimeUnit.MILLISECONDS)));
                  }
               } else {
                  loader.join();
               }
            } catch (InterruptedException e) {
               loader.interrupt();
               return errorResponse("Exception while interrupting loading thread.", e);
            }
         }
      }
      slaveState.remove(BACKGROUND_LOADERS);
      return successfulResponse();
   }
}
