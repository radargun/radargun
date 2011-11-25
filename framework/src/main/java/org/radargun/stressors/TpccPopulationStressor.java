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
import org.radargun.tpcc.TpccTools;

/**
 * Populate <code>numWarehouses</code> Warehouses in cache.
 *
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public class TpccPopulationStressor implements CacheWrapperStressor {

   private static Log log = LogFactory.getLog(TpccPopulationStage.class);

   private int numWarehouses;

   private long cLastMask = 255L;

   private long olIdMask = 8191L;

   private long cIdMask = 1023L;

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
         log.warn("Received exception during cache population" + e.getMessage());
      }
      return null;
   }

   public void performPopulationOperations(CacheWrapper w) throws Exception {
      this.wrapper = w;
      log.info("Performing population...");
      new TpccPopulation(this.wrapper, this.numWarehouses, this.slaveIndex, this.numSlaves, this.cLastMask, this.olIdMask, this.cIdMask);
      log.info("Population ended");
   }

   public void setNumWarehouses(int numWarehouses) {

      this.numWarehouses = numWarehouses;
   }

   public void setSlaveIndex(int slaveIndex) {

      this.slaveIndex = slaveIndex;
   }

   public void setNumSlaves(int numSlaves) {

      this.numSlaves = numSlaves;
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
      return "TpccPopulationStressor{" +
            "numWarehouses=" + this.numWarehouses +
            "cLastMask=" + TpccTools.A_C_LAST +
            "olIdMask=" + TpccTools.A_OL_I_ID +
            "cIdMask=" + TpccTools.A_C_ID +
            "slaveIndex=" + this.slaveIndex +
            "numSlaves=" + this.numSlaves + "}";
   }


   public void destroy() throws Exception {

      //Don't destroy data in cache!
   }

}
