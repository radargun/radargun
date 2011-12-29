package org.radargun.stages;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.state.MasterState;
import org.radargun.stressors.WarmupStressor;

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
 * @deprecated Do not use, since this only really warms up the PutGetStressor in a very simplistic access path.  Each
 * <tt>*BenchmarkStage</tt> should have its own customized warmup.
 */
@Deprecated
public class WarmupStage extends AbstractDistStage {

   private int operationCount = 10000;

   private int numThreads = 5;

   public DistStageAck executeOnSlave() {
      log.warn("**** DEPRECATED. This warmup is likely to not properly warm up your JIT compiler, giving you false results. Please see the javadoc of WarmupStage.java for more details.");
      DefaultDistStageAck ack = newDefaultStageAck();
      CacheWrapper wrapper = slaveState.getCacheWrapper();
      if (wrapper == null) {
         log.info("Not executing any test as the wrapper is not set up on this slave ");
         return ack;
      }
      long startTime = System.currentTimeMillis();
      warmup(wrapper);
      long duration = System.currentTimeMillis() - startTime;
      log.info("The warmup took: " + (duration / 1000) + " seconds.");
      ack.setPayload(duration);
      return ack;
   }

   private void warmup(CacheWrapper wrapper) {
      WarmupStressor warmupStressor = new WarmupStressor();
      warmupStressor.setBucket("warmup_bucket" + String.valueOf(getSlaveIndex()) + "_");
      warmupStressor.setKeyPrefix("warmup_key_" + String.valueOf(getSlaveIndex()) + "_");
      warmupStressor.setOperationCount(operationCount);
      warmupStressor.setNumThreads(numThreads);
      warmupStressor.stress(wrapper);
   }

   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      logDurationInfo(acks);
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dAck = (DefaultDistStageAck) ack;
         if (log.isTraceEnabled()) {
            log.trace("Warmup on slave " + dAck.getSlaveIndex() + " finished in " + dAck.getPayload() + " millis.");
         }
      }
      return true;
   }

   public void setOperationCount(int operationCount) {
      this.operationCount = operationCount;
   }

   public void setNumThreads(int numThreads) {
      this.numThreads = numThreads;
   }

   @Override
   public String toString() {
      return "WarmupStage {" +
            "operationCount=" + operationCount + ", " + super.toString();
   }
}
