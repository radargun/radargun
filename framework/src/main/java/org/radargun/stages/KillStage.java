package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.state.MasterState;

/**
 * 
 * Will simulate a node kill on specified nodes. If the used CacheWrapper doesn't implement killable
 * it will only do tearDown()
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 */
public class KillStage extends AbstractDistStage {

   private boolean tearDown = false;
   private boolean async = false;

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
         KillHelper.kill(slaveState, tearDown, async, ack);
      } else {
         log.trace("Ignoring kill request, not targeted for this slave");
      }
      return ack;
   }

   @Override
   public String toString() {
      return "KillStage {tearDown=" + tearDown + ", " + super.toString();
   }

   public void setTearDown(boolean tearDown) {
      this.tearDown = tearDown;
   }
   
   public void setAsync(boolean async) {
      this.async = async;
   }

}
