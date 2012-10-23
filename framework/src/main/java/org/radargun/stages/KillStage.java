package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.stages.helpers.KillHelper;
import org.radargun.stages.helpers.RoleHelper;
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
   private String role;
   private long delayExecution;

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
      if ((role != null && RoleHelper.hasRole(slaveState, role)) || (slaves != null && slaves.contains(getSlaveIndex()))) {
         if (delayExecution > 0) {
            Thread t = new Thread() {
               @Override
               public void run() {
                  try {
                     Thread.sleep(delayExecution);
                  } catch (InterruptedException e) {                    
                  }
                  KillHelper.kill(slaveState, tearDown, async, null);
               }
            };
            t.start();
         } else {
            KillHelper.kill(slaveState, tearDown, async, ack);
         }
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
   
   public void setRole(String role) {
      this.role = role;
   }
   
   public void setDelayExecution(long milliseconds) {
      this.delayExecution = milliseconds;
   }
}
