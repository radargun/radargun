package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.state.MasterState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The warmup stage for web session benchmarks.  This ensures the same access paths are used for the warmup and the
 * actual benchmark.
 *
 * @author Manik Surtani
 */
public class WebSessionWarmupStage extends WebSessionBenchmarkStage {

   private static final Set<String> WARMED_UP_CONFIGS = new HashSet<String>(2);

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = new DefaultDistStageAck(slaveIndex, slaveState.getLocalAddress());
      this.cacheWrapper = slaveState.getCacheWrapper();
      if (cacheWrapper == null) {
         log.info("Not running test on this slave as the wrapper hasn't been configured.");
         return result;
      }

      String configName = cacheWrapper.getClass().getName() + " - " + cacheWrapper.getInfo();

      if (!WARMED_UP_CONFIGS.contains(configName)) {

         try {
            long startTime = System.currentTimeMillis();
            doWork();
            long duration = System.currentTimeMillis() - startTime;
            log.info("The warmup took: " + (duration / 1000) + " seconds.");
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
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      logDurationInfo(acks);
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dAck = (DefaultDistStageAck) ack;
         if (dAck.isError())
            log.warn("Caught error on slave " + dAck.getSlaveIndex() + " when running " + getClass().getSimpleName() + ".  Error details:" + dAck.getErrorMessage());
      }
      return true;
   }

   @Override
   public String toString() {
      return "Warmup for " + super.toString();
   }
}
