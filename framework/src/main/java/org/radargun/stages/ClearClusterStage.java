package org.radargun.stages;

import java.util.ArrayList;
import java.util.Random;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.stressors.BackgroundOpsManager;
import org.radargun.utils.Utils;

/**
 * Distributed stage that will clear the content of the cache wrapper on each slave.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Removes all data from the cache")
public class ClearClusterStage extends AbstractDistStage {

   public ClearClusterStage() {
      /* The clear command should be executed only once to clear the whole cache, not only this node.
       * With optimistic locking the clear could timeout if executed on all nodes (this causes maximal contention on all
       * keys) */
      slaves = new ArrayList<Integer>();
      slaves.add(0);
   }

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck defaultDistStageAck = newDefaultStageAck();
      BackgroundOpsManager.beforeCacheWrapperClear(slaveState);
      CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
      if (cacheWrapper == null) {
         log.info("This slave is dead, cannot clear cache.");
         return defaultDistStageAck;
      }
      for (int i = 0; i < 5; i++) {
         try {
            log.info(Utils.printMemoryFootprint(true));
            if (slaves == null || slaves.contains(getSlaveIndex())) {
               cacheWrapper.empty();
            } else {
               int size;
               for (int count = new Random().nextInt(20) + 10; count > 0 && (size = cacheWrapper.getLocalSize()) > 0; --count) {
                  log.debug("Waiting until the cache gets empty (contains " + size + " entries)");
                  Thread.sleep(1000);
               }
               if ((size = cacheWrapper.getLocalSize()) > 0) {
                  log.error("The cache was not cleared from another node (contains " + size + " entries), clearing locally");
                  cacheWrapper.empty();
               }
            }
            return defaultDistStageAck;
         } catch (Exception e) {
            log.warn("Failed to clear cache(s)", e);
         } finally {
            System.gc();
            log.info(Utils.printMemoryFootprint(false));
         }
      }
      defaultDistStageAck.setPayload("WARN!! Issues while clearing the cache!!!");
      return defaultDistStageAck;
   }

   @Override
   public String toString() {
      return "ClearClusterStage {" + super.toString();
   }
}
