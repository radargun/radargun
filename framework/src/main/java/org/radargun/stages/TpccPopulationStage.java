
package org.radargun.stages;

import java.util.List;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.state.MasterState;
import org.radargun.stressors.TpccPopulationStressor;
import org.radargun.stressors.WarmupStressor;

/**
 * This stage shuld be run before the <b>TpccBenchmarkStage</b>. It will perform the population of
 * <b>numWarehouses</b> warehouses in cache. Note: this stage won't clear the added data from
 * slave.
 * <pre>
 * Params:
 *       - numWarehouses : the number of warehouses to be populated.
 * </pre>
 *
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */

public class TpccPopulationStage extends AbstractDistStage{
   
   /**
    * number of Warehouses
    */
   private int numWarehouses = 1;
   
   /**
    * mask used to generate non-uniformly distributed random customer last names
    */
   private long cLastMask = 255;
   
   /**
    * mask used to generate non-uniformly distributed random item numbers
    */
   private long olIdMask = 8191;
   
   /**
    * mask used to generate non-uniformly distributed random customer numbers
    */
   private long cIdMask = 1023;

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      CacheWrapper wrapper = slaveState.getCacheWrapper();
      if (wrapper == null) {
         log.info("Not executing any test as the wrapper is not set up on this slave ");
         return ack;
      }
      long startTime = System.currentTimeMillis();
      populate(wrapper);
      long duration = System.currentTimeMillis() - startTime;
      log.info("The population took: " + (duration / 1000) + " seconds.");
      ack.setPayload(duration);
      return ack;
   }

   private void populate(CacheWrapper wrapper) {
      TpccPopulationStressor populationStressor = new TpccPopulationStressor();
      populationStressor.setNumWarehouses(numWarehouses);
      populationStressor.setSlaveIndex(getSlaveIndex());
      populationStressor.setNumSlaves(getActiveSlaveCount());
      populationStressor.setCLastMask(this.cLastMask);
      populationStressor.setOlIdMask(this.olIdMask);
      populationStressor.setCIdMask(this.cIdMask);
      populationStressor.stress(wrapper);
   }

   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      logDurationInfo(acks);
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dAck = (DefaultDistStageAck) ack;
         if (log.isTraceEnabled()) {
            log.trace("Tpcc population on slave " + dAck.getSlaveIndex() + " finished in " + dAck.getPayload() + " millis.");
         }
      }
      return true;
   }

   public void setNumWarehouses(int numWarehouses) {
      this.numWarehouses = numWarehouses;
   }
   
   public void setCLastMask(long cLastMask) {
      this.cLastMask = cLastMask;
   }

   public void setOlIdMask(long olIdMask) {
      this.olIdMask = olIdMask;
   }

   public void setCIdMask(long cIdMask) {
      this.cIdMask = cIdMask;
   }

   @Override
   public String toString() {
      return "TpccPopulationStage {" +
            "numWarehouses=" + numWarehouses + 
            ", cLastMask=" + cLastMask +
            ", olIdMask=" + olIdMask +
            ", cIdMask=" + cIdMask +   
            ", " + super.toString();
   }

}
