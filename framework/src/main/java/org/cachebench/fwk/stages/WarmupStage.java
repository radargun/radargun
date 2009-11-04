package org.cachebench.fwk.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.fwk.DistStageAck;
import org.cachebench.CacheWrapper;

import java.util.List;
import java.util.Arrays;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class WarmupStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(WarmupStage.class);

   private int operationCount = 10000;

   public DistStageAck executeOnNode() {
      CacheWrapper wrapper = nodeState.getCacheWrapper();
      long startTime = System.currentTimeMillis();
      try {
         performWarmupOperations(wrapper);
      } catch (Exception e) {
         log.warn("Received exception durring cache warmup" + e.getMessage());
      }
      long duration = System.currentTimeMillis() - startTime;
      log.info("The warmup took: " + (duration / 1000) + " seconds.");
      try {
         wrapper.empty();
      } catch (Exception e) {
         log.warn("Received exception durring cache warmup", e);
      }
      DefaultDistStageAck ack = newDefaultStageAck();
      ack.setPayload(duration);
      return ack;
   }

   public boolean processAckOnServer(List<DistStageAck> acks) {
      log.info("Warmup finished on all nodes.");
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dAck = (DefaultDistStageAck) ack;
         if (log.isTraceEnabled()) {
            log.trace("Warmup on node " + dAck.getNodeIndex() + " finished in " + dAck.getPayload() + " millis.");
         }
      }
      return true;
   }


   public void performWarmupOperations(CacheWrapper wrapper) throws Exception {
      log.info("Cache launched, performing " + (Integer) operationCount + " put and get operations ");
      List<String> path = Arrays.asList("a", "b", "c");
      for (int i = 0; i < operationCount; i++) {
         try {
            wrapper.put(path, String.valueOf((Integer) operationCount), String.valueOf((Integer) operationCount));
         }
         catch (Throwable e) {
            log.trace("Exception on cache warmup", e);
         }
      }

      for (int i = 0; i < operationCount; i++) {
         wrapper.get(path, String.valueOf((Integer) operationCount));
      }
      log.trace("Cache warmup ended!");
   }


   public void setOperationCount(int operationCount) {
      this.operationCount = operationCount;
   }
}
