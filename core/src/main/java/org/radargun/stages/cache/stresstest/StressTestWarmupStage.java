package org.radargun.stages.cache.stresstest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;

/**
 * The warmup stage for stress test benchmarks.  This ensures the same access paths are used for the warmup and the
 * actual benchmark.
 *
 * @author Manik Surtani &lt;msurtani@gmail.com&gt;
 */
@Stage(doc = "Warmup stage for stress test benchmarks.", deprecatedName = "WebSessionWarmup")
public class StressTestWarmupStage extends StressTestStage {

   private static final Set<String> WARMED_UP_CONFIGS = new HashSet<String>(2);

   @Override
   public DistStageAck executeOnSlave() {
      if (!shouldExecute()) {
         log.info("The stage should not run on this slave");
         return successfulResponse();
      }
      if (!isServiceRunnning()) {
         log.info("Not running test on this slave as the service is not running.");
         return successfulResponse();
      }

      String configName = slaveState.getServiceName() + "-" + slaveState.getConfigName();
      if (!WARMED_UP_CONFIGS.contains(configName)) {

         try {
            startNanos = System.nanoTime();
            execute();
            WARMED_UP_CONFIGS.add(configName);
            return successfulResponse();
         } catch (Exception e) {
            return errorResponse("Exception while running " + getClass().getSimpleName(), e);
         }
      } else {
         log.info("Skipping warmup, this has already been done for this configuration on this node.");
         return successfulResponse();
      }
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      return defaultProcessAck(acks);
   }
}
