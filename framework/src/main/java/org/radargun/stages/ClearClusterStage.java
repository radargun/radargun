package org.radargun.stages;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.utils.Utils;

import java.util.ArrayList;
import java.util.Random;

/**
 * Distributed stage that will clear the content of the cache wrapper on each slave.
 *
 * @author Mircea.Markus@jboss.com
 */
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
      CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
      for (int i = 0; i < 5; i++) {
         try {
            log.info(Utils.printMemoryFootprint(true));
            if (slaves == null || slaves.contains(getSlaveIndex())) {
               cacheWrapper.empty();
            } else {
               for (int count = new Random().nextInt(20) + 10; count > 0 && cacheWrapper.getLocalSize() > 0; --count) {
                  log.debug("Waiting until the cache gets empty");
                  Thread.sleep(1000);
               }
               if (cacheWrapper.getLocalSize() > 0) {
                  log.error("The cache was not cleared from another node, clearing locally");
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
