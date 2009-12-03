package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.DistStageAck;
import org.cachebench.CacheWrapper;

/**
 * Distributed stage that will stop the cache wrapper on each slave.
 *
 * @author Mircea.Markus@jboss.com
 */
public class DestroyWrapperStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(DestroyWrapperStage.class);

   public DestroyWrapperStage() {
   }

   public DistStageAck executeOnSlave() {
      log.info("Received shutdown request from master...");
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
         if (cacheWrapper != null) {
            cacheWrapper.tearDown();
            log.info("Cache wrapper successfully destroyed!");
            slaveState.setCacheWrapper(null);
         } else {
            log.info("No cache wrapper deployed on this slave, nothing to do.");
            return ack;
         }
      } catch (Exception e) {
         log.warn("Problems shutting down the slave", e);
         ack.setError(true);
         ack.setRemoteException(e);
         return ack;
      }
      log.info("Cache wrapper successfully tearDown.");
      return ack;
   }
}
