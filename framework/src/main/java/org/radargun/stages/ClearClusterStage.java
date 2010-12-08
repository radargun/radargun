package org.radargun.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.utils.Utils;

/**
 * Distributed stage that will clear the content of the cache wrapper on each slave.
 *
 * @author Mircea.Markus@jboss.com
 */
public class ClearClusterStage extends AbstractDistStage {

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck defaultDistStageAck = newDefaultStageAck();
      CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
      for (int i = 0; i < 5; i++) {
         try {
            log.info(Utils.printMemoryFootprint(true));
            cacheWrapper.empty();
            return defaultDistStageAck;
         } catch (Exception e) {
            log.warn(e);
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
