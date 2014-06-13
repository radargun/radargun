package org.radargun.stages;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.radargun.DistStage;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.helpers.RoleHelper;
import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Lifecycle;
import org.radargun.utils.Utils;

/**
 * Support class for distributed stages.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "")
public abstract class AbstractDistStage extends AbstractStage implements DistStage {
   protected Log log = LogFactory.getLog(getClass());

   /**
    * This field is filled in only on master node, on slave it is set to null
    */
   protected MasterState masterState;
   /**
    * This field is filled in only on slave node, on master it is set to null
    */
   protected SlaveState slaveState;

   @Property(doc = "Specifies on which slaves this stage should actively run. " +
         "The result set is intersection of specified slaves, groups and roles. Default is all slaves.")
   protected Set<Integer> slaves;

   @Property(doc = "Specifies in which groups this stage should actively run. " +
         "The result set is intersection of specified slaves, groups and roles. Default is all groups.")
   protected Set<String> groups;

   @Property(doc = "Specifies on which slaves this stage should actively run, by their roles. " +
         "The result set is intersection of specified slaves, groups and roles. " +
         "Supported roles are " + RoleHelper.SUPPORTED_ROLES + ". Default is all roles.")
   protected Set<RoleHelper.Role> roles;


   @InjectTrait
   protected Lifecycle lifecycle;

   @Override
   public void initOnMaster(MasterState masterState) {
      this.masterState = masterState;
   }

   @Override
   public void initOnSlave(SlaveState slaveState) {
      this.slaveState = slaveState;
   }

   public boolean isServiceRunnning() {
      return lifecycle == null || lifecycle.isRunning();
   }

   @Override
   public boolean shouldExecute() {
      boolean execBySlave = slaves == null || slaves.contains(slaveState.getSlaveIndex());
      boolean execByGroup = groups == null || groups.contains(slaveState.getGroupName());
      boolean execByRole = roles == null || RoleHelper.hasAnyRole(slaveState, roles);
      return execBySlave && execByGroup && execByRole;
   }

   protected Set<Integer> getExecutingSlaves() {
      Set<Integer> slaves = this.slaves;
      if (slaves == null) {
         slaves = new TreeSet<Integer>();
         for (int i = 0; i < slaveState.getClusterSize(); ++i) {
            slaves.add(i);
         }
      }
      if (groups != null) {
         for (Iterator<Integer> it = slaves.iterator(); it.hasNext();) {
            int slaveIndex = it.next();
            if (!groups.contains(slaveState.getGroupName(slaveIndex))) {
               it.remove();
            }
         }
      }
      // roles are not supported here as we can't determine if the slave has some role remotely
      return slaves;
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      boolean success = true;
      logDurationInfo(acks);
      for (DistStageAck ack : acks) {
         if (ack.isError()) {
            log.warn("Received error ack " + ack);
            return false;
         } else {
            log.trace("Received success ack " + ack);
         }
      }
      if (log.isTraceEnabled())
         log.trace("All ack messages were successful");
      return success;
   }

   protected void logDurationInfo(List<? extends DistStageAck> acks) {
      if (!log.isInfoEnabled()) return;

      String processingDuration = "Durations [";
      boolean first = true;
      for (DistStageAck ack: acks) {
         if (first) first = false;
         else processingDuration += ", ";
         processingDuration += ack.getSlaveIndex() + " = " + Utils.prettyPrintMillis(ack.getDuration());
      }
      log.info("Received responses from all " + acks.size() + " slaves. " + processingDuration + "]");
   }

   protected DistStageAck errorResponse(String message) {
      log.error(message);
      return new DistStageAck(slaveState).error(message, null);
   }

   protected DistStageAck errorResponse(String message, Exception e) {
      log.error(message, e);
      return new DistStageAck(slaveState).error(message, e);
   }

   protected DistStageAck successfulResponse() {
      return new DistStageAck(slaveState);
   }
}
