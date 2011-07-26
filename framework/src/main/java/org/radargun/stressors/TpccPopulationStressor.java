package org.radargun.stressors;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.CacheWrapperStressor;
import org.radargun.stages.TpccPopulationStage;
import org.radargun.tpcc.TpccPopulation;

/**
 * Populate <code>numWarehouses</code> Warehouses in cache.
 *
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public class TpccPopulationStressor implements CacheWrapperStressor{
   
   private static Log log = LogFactory.getLog(TpccPopulationStage.class);
   
   private int numWarehouses;
   
   private int slaveIndex;

   private int numSlaves;

   private CacheWrapper wrapper;

   public Map<String, String> stress(CacheWrapper wrapper) {
      if (wrapper == null) {
         throw new IllegalStateException("Null wrapper not allowed");
      }
      try {
         log.info("Performing Population Operations");
         performPopulationOperations(wrapper);
      } catch (Exception e) {
         log.warn("Received exception durring cache population" + e.getMessage());
      }
      return null;
   }

   public void performPopulationOperations(CacheWrapper w) throws Exception {
      this.wrapper = w;
      log.info("Peforming population...");
      new TpccPopulation(this.wrapper, this.numWarehouses, this.slaveIndex, this.numSlaves);
      log.info("Population ended");
   }
   
   public void setNumWarehouses(int numWarehouses){
      
      this.numWarehouses = numWarehouses;
   }
   
   public void setSlaveIndex(int slaveIndex){
      
      this.slaveIndex = slaveIndex;
   }
   
   public void setNumSlaves(int numSlaves){
      
      this.numSlaves = numSlaves;
   }

   @Override
   public String toString() {
      return "TpccPopulationStressor{" +
            "numWarehouses=" + this.numWarehouses +
            "slaveIndex=" + this.slaveIndex +
            "numSlaves=" + this.numSlaves + "}";
   }


   public void destroy() throws Exception {
      wrapper.empty();
      wrapper = null;
   }

}
