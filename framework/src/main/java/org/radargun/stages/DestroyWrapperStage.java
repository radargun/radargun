package org.radargun.stages;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.Slave;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.MasterState;
import org.radargun.stressors.BackgroundStats;
import org.radargun.utils.Utils;

import java.util.List;

import static org.radargun.utils.Utils.*;

/**
 * Distributed stage that will stop the cache wrapper on each slave.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Distributed stage that will stop the cache wrapper on each slave.")
public class DestroyWrapperStage extends AbstractDistStage {
   private static final String FREEMEM_KB = DestroyWrapperStage.class.getSimpleName() + ":FreeMem_KB";

   @Property(doc = "Specifies whether the check for amount of free memory should be performed. Default is true.")
   private boolean enforceMemoryThrashHold = true;

   @Property(doc = "If the free memory after wrapper destroy and System.gc() is below percentage specified in this property the benchmark will stop. Default is 95.")
   private byte memoryThreshold = 95;

   private Long initialFreeMemoryKb;

   public DestroyWrapperStage() {
   }

   @Override
   public void initOnMaster(MasterState masterState, int slaveIndex) {
      super.initOnMaster(masterState, slaveIndex);
      this.initialFreeMemoryKb = (Long) masterState.get(FREEMEM_KB + "_" +  getSlaveIndex());
   }

   public DistStageAck executeOnSlave() {
      log.info("Received destroy cache wrapper request from master...");
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
         if (cacheWrapper != null) {
            BackgroundStats.beforeCacheWrapperDestroy(slaveState);
            cacheWrapper.tearDown();
            for (int i = 0; i < 120; i++) {
               if (cacheWrapper.getNumMembers() <= 0) break; //negative value might be returned by impl that do not support this method
               log.info("There are still: " + cacheWrapper.getNumMembers() + " members in the cluster. Waiting for them to turn off.");
               Thread.sleep(1000);
            }
            slaveState.setCacheWrapper(null);
            //reset the class loader to SystemClassLoader
            Thread.currentThread().setContextClassLoader(Slave.class.getClassLoader());
            log.info("Cache wrapper successfully tearDown. Number of members is the cluster is: " + cacheWrapper.getNumMembers());
         } else {
            log.info("No cache wrapper deployed on this slave, nothing to do.");
            return returnAck(ack);
         }
      } catch (Exception e) {
         log.warn("Problems shutting down the slave", e);
         ack.setError(true);
         ack.setRemoteException(e);
         return returnAck(ack);
      } finally {
         log.info(printMemoryFootprint(true));
         if (enforceMemoryThrashHold && notFirstRun()) {
            doEnforceMemoryThrashHold();
         } else {
            System.gc();
         }
         log.info(printMemoryFootprint(false));
      }
      return returnAck(ack);
   }

   private boolean notFirstRun() {
      return initialFreeMemoryKb != null;
   }


   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      boolean result = super.processAckOnMaster(acks, masterState);
      if (masterState.get(FREEMEM_KB) == null) {
         masterState.put(FREEMEM_KB, "");
         for (DistStageAck distStageAck : acks) {
            String key = FREEMEM_KB + "_" + distStageAck.getSlaveIndex();
            Long freeMemoryKB = (Long) ((DefaultDistStageAck) distStageAck).getPayload();
            log.info("Node " + distStageAck.getSlaveIndex() + " has an initial free memory of: " + Utils.memString(freeMemoryKB * 1024));
            masterState.put(key, freeMemoryKB);
         }
      }
      return result;
   }

   public void setEnforceMemoryThrashHold(boolean enforceMemoryThrashHold) {
      this.enforceMemoryThrashHold = enforceMemoryThrashHold;
   }

   public void setMemoryThreshold(byte memoryThreshold) {
      this.memoryThreshold = memoryThreshold;
   }

   private DistStageAck returnAck(DefaultDistStageAck ack) {
      ack.setPayload(getFreeMemoryKb());
      return ack;
   }

   private void doEnforceMemoryThrashHold() {
      long actualPercentage = -1;
      long freeMemory = -1;
      for (int i = 0; i < 30; i++) {
         System.gc();
         freeMemory = getFreeMemoryKb();
         // initialFreeMemoryKb  ... 100%
         // freeMemory           ... x% (actualPercentage)
         actualPercentage = (freeMemory * 100) / initialFreeMemoryKb;
         if (actualPercentage >= memoryThreshold) break;
         Utils.seep(1000);
      }
      log.info("Free memory: " + memString(freeMemory, "kb") + " (" + actualPercentage + "% from the initial free memory - " + memString(initialFreeMemoryKb, "kb") + ")");
      if (actualPercentage < memoryThreshold) {
         String msg = "Actual percentage of memory smaller than expected!";
         log.error(msg);
         throw new RuntimeException(msg);
      }
   }


   @Override
   public String toString() {
      return "DestroyWrapperStage {" + super.toString();
   }
}
