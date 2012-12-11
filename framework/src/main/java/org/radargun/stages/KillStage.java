package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stages.helpers.KillHelper;
import org.radargun.stages.helpers.RoleHelper;
import org.radargun.state.MasterState;

/**
 * 
 * Will simulate a node kill on specified nodes. If the used CacheWrapper doesn't implement killable
 * it will only do tearDown()
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Shutdowns or kills (simulates node crash) one or more nodes.")
public class KillStage extends AbstractDistStage {

   @Property(doc = "If set to true, the nodes should be shutdown. Default is false = simulate node crash.")
   private boolean tearDown = false;

   @Property(doc = "If set to true the benchmark will not wait until the node is killed. Default is false.")
   private boolean async = false;

   @Property(doc = "Instead of specifying concrete slaves we may choose a victim based on his role in the cluster. " +
         "Supported roles are " + RoleHelper.SUPPORTED_ROLES + ". By default no role is specified.")
   private String role;

   @Property(converter = TimeConverter.class, doc = "If this value is positive the stage will spawn a thread which " +
         "will kill the node after the delay. The stage will not wait for anything. By default the kill is immediate " +
         "and synchronous.")
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
