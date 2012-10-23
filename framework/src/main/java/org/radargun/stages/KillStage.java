package org.radargun.stages;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.Killable;
import org.radargun.state.MasterState;
import org.radargun.stressors.BackgroundStats;

/**
 * 
 * Will simulate a node kill on specified nodes. If the used CacheWrapper doesn't implement killable
 * it will only do tearDown()
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 */
public class KillStage extends AbstractDistStage {

   private boolean tearDown = false;

   public KillStage() {
      // nada
   }

   @Override
   public void initOnMaster(MasterState masterState, int slaveIndex) {
      super.initOnMaster(masterState, slaveIndex);
   }

   public DistStageAck executeOnSlave() {
      log.info("Received kill request from master...");
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaves != null && slaves.contains(getSlaveIndex())) {
         try {
            CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
            if (cacheWrapper != null) {
               BackgroundStats.beforeCacheWrapperDestroy(slaveState);
               if (tearDown) {
                  log.info("Tearing down cache wrapper.");
                  cacheWrapper.tearDown();
               } else if (cacheWrapper instanceof Killable) {
                  log.info("Killing cache wrapper.");
                  ((Killable) cacheWrapper).kill();
               } else {
                  log.info("CacheWrapper is not killable, calling tearDown instead");
                  cacheWrapper.tearDown();
               }
            } else {
               log.info("No cache wrapper deployed on this slave, nothing to do.");
            }
            slaveState.setCacheWrapper(null);
            return ack;
         } catch (Exception e) {
            log.error("Error while killing slave", e);
            ack.setError(true);
            ack.setRemoteException(e);
            return ack;
         } finally {
            System.gc();
         }
      } else {
         log.trace("Ignoring kill request, not targeted for this slave");
         return ack;
      }
   }

   @Override
   public String toString() {
      return "KillStage {tearDown=" + tearDown + ", " + super.toString();
   }

   public void setTearDown(boolean tearDown) {
      this.tearDown = tearDown;
   }

}
