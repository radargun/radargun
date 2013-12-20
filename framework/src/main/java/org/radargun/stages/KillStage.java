package org.radargun.stages;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stages.helpers.KillHelper;
import org.radargun.stages.helpers.RoleHelper;
import org.radargun.stages.helpers.StartStopTime;
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

   private static final String KILL_DELAY_THREAD = "_KILL_DELAY_THREAD_";

   @Property(doc = "If set to true, the nodes should be shutdown. Default is false = simulate node crash.")
   private boolean tearDown = false;

   @Property(doc = "If set to true the benchmark will not wait until the node is killed. Default is false.")
   private boolean async = false;

   @Property(doc = "Instead of specifying concrete slaves we may choose a victim based on his role in the cluster. " +
         "Supported roles are " + RoleHelper.SUPPORTED_ROLES + ". By default no role is specified.")
   private RoleHelper.Role role;

   @Property(converter = TimeConverter.class, doc = "If this value is positive the stage will spawn a thread which " +
         "will kill the node after the delay. The stage will not wait for anything. By default the kill is immediate " +
         "and synchronous.")
   private long delayExecution;

   @Property(doc="If set, the stage will not kill any node but will wait until the delayed execution is finished. " +
         "Default is false")
   private boolean waitForDelayed = false;

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
      if (waitForDelayed) {
         Thread t = (Thread) slaveState.get(KILL_DELAY_THREAD);
         if (t != null) {
            try {
               t.join();
               slaveState.remove(KILL_DELAY_THREAD);
            } catch (InterruptedException e) {
               String error = "Interrupted while waiting for kill to be finished.";
               log.error(error, e);
               ack.setErrorMessage(error);
               ack.setRemoteException(e);
               ack.setError(true);
            }
         } else {
            log.info("No delayed execution found in history.");
         }
      } else if ((role != null && RoleHelper.hasRole(slaveState, role))
            || (slaves != null && slaves.contains(getSlaveIndex()))) {
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
            slaveState.put(KILL_DELAY_THREAD, t);
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
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      if (!super.processAckOnMaster(acks, masterState)) {
         return false;
      }
      for (DistStageAck ack : acks) {
         StartStopTime times = ((StartStopTime) ((DefaultDistStageAck) ack).getPayload());
         if (times != null && times.getStopTime() >= 0) {
            CsvReportGenerationStage.addResult(masterState, ack.getSlaveIndex(), KillHelper.STOP_TIME, times.getStopTime());
         }
      }
      return true;
   }
}
