package org.radargun.stages.cache.stresstest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.stages.DefaultDistStageAck;

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
      DefaultDistStageAck result = new DefaultDistStageAck(slaveState.getSlaveIndex(), slaveState.getLocalAddress());
      if (slaves != null && !slaves.contains(slaveState.getSlaveIndex())) {
         log.info(String.format("The stage should not run on this slave (%d): slaves=%s", slaveState.getSlaveIndex(), slaves));
         return result;
      }
      if (!isServiceRunnning()) {
         log.info("Not running test on this slave as the service is not running.");
         return result;
      }

      String configName = slaveState.getServiceName() + "-" + slaveState.getConfigName();
      if (!WARMED_UP_CONFIGS.contains(configName)) {

         try {
            startNanos = System.nanoTime();
            execute();
            result.setPayload(duration);
            WARMED_UP_CONFIGS.add(configName);
            return result;
         } catch (Exception e) {
            log.warn("Exception while running " + getClass().getSimpleName(), e);
            result.setError(true);
            result.setRemoteException(e);
            return result;
         }
      } else {
         log.info("Skipping warmup, this has already been done for this configuration on this node.");
         return result;
      }
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      logDurationInfo(acks);
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dAck = (DefaultDistStageAck) ack;
         if (dAck.isError())
            log.warn("Caught error on slave " + dAck.getSlaveIndex() + " when running " + getClass().getSimpleName() + ".  Error details:" + dAck.getErrorMessage());
      }
      return true;
   }
}
