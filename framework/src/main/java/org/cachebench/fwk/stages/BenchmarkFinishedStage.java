package org.cachebench.fwk.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.fwk.DistStageAck;

import java.util.List;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class BenchmarkFinishedStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(BenchmarkFinishedStage.class);

   public BenchmarkFinishedStage() {
      setSkipOnFailure(false);
   }

   public DistStageAck executeOnNode() {
      log.info("Received shutdown request from server...");
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         nodeState.getCacheWrapper().tearDown();
      } catch (Exception e) {
         log.warn("Problems shutting down the node", e);
         ack.setError(true);
         ack.setRemoteException(e);
         return ack;
      }
      log.info("Cache wrapper successfully tearDown.");
      return ack;
   }

   public boolean processAckOnServer(List<DistStageAck> acks) {
      for (DistStageAck anAck : acks) {
         DefaultDistStageAck ack = (DefaultDistStageAck) anAck;
         if (ack.isError()) {
            log.warn("Issues shutting down node: " + ack.getNodeDescription(), ack.getRemoteException());
         }
      }
      return true;
   }
}
