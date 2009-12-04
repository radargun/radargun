package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.DistStageAck;

import java.util.List;

/**
 * This stage shuld be run before the actual test, in order to activate JIT compiler. It will perform
 * <b>operationCount</b> puts and then gets on the cache wrapper. Note: this stage won't clear the added data from
 * slave.
 * <pre>
 * Params:
 *       - operationCount : the number of operations to perform.
 * </pre>
 *
 * @author Mircea.Markus@jboss.com
 */
public class WarmupStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(WarmupStage.class);

   private int operationCount = 10000;

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      CacheWrapper wrapper = slaveState.getCacheWrapper();
      if (wrapper == null) {
         log.info("Not executing any test as the wrapper is not set up on this slave ");
         return ack;
      }
      long startTime = System.currentTimeMillis();
      try {
         performWarmupOperations(wrapper);
      } catch (Exception e) {
         log.warn("Received exception durring cache warmup" + e.getMessage());
      }
      long duration = System.currentTimeMillis() - startTime;
      log.info("The warmup took: " + (duration / 1000) + " seconds.");
      ack.setPayload(duration);
      return ack;
   }

   public boolean processAckOnMaster(List<DistStageAck> acks) {
      logDurationInfo(acks);
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dAck = (DefaultDistStageAck) ack;
         if (log.isTraceEnabled()) {
            log.trace("Warmup on slave " + dAck.getSlaveIndex() + " finished in " + dAck.getPayload() + " millis.");
         }
      }
      return true;
   }


   public void performWarmupOperations(CacheWrapper wrapper) throws Exception {
      log.info("Cache launched, performing " + (Integer) operationCount + " put and get operations ");
      String path = "a_b_c" + slaveIndex;
      for (int i = 0; i < operationCount; i++) {
         try {
            wrapper.put(path, getSlaveIndex() + String.valueOf((Integer) operationCount), String.valueOf(i));
         }
         catch (Throwable e) {
            log.trace("Exception on cache warmup", e);
         }
      }

      for (int i = 0; i < operationCount; i++) {
         wrapper.get(path, getSlaveIndex() + String.valueOf((Integer) operationCount));
      }
      log.trace("Cache warmup ended!");
   }


   public void setOperationCount(int operationCount) {
      this.operationCount = operationCount;
   }

   @Override
   public String toString() {
      return "WarmupStage{" +
            "operationCount=" + operationCount + super.toString();
   }
}
