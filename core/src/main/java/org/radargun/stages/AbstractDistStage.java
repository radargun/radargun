package org.radargun.stages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.radargun.DistStage;
import org.radargun.DistStageAck;
import org.radargun.StageResult;
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

   /**
    * This field is filled in only on master node, on slave it is set to null
    */
   protected MasterState masterState;
   /**
    * This field is filled in only on slave node, on master it is set to null
    */
   protected SlaveState slaveState;

   protected List<Integer> executingSlaves;

   public boolean isServiceRunning() {
      return lifecycle == null || lifecycle.isRunning();
   }

   @Override
   public boolean shouldExecute() {
      boolean execBySlave = slaves == null || slaves.contains(slaveState.getSlaveIndex());
      boolean execByGroup = groups == null || groups.contains(slaveState.getGroupName());
      boolean execByRole = roles == null || RoleHelper.hasAnyRole(slaveState, roles);
      return execBySlave && execByGroup && execByRole;
   }

   /**
    * @return List of slave indices that are executing this stage
    *         (unless the slaves/groups was evaluated differently on each slave).
    *         The order is guaranteed to be same across slaves.
    */
   protected List<Integer> getExecutingSlaves() {
      if (executingSlaves != null) {
         return executingSlaves;
      }
      List<Integer> slaves;
      if (this.slaves == null) {
         slaves = new ArrayList<>();
         for (int i = 0; i < slaveState.getClusterSize(); ++i) {
            slaves.add(i);
         }
      } else {
         slaves = new ArrayList<>(this.slaves);
         Collections.sort(slaves);
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
      return executingSlaves = slaves;
   }

   protected int getExecutingSlaveIndex() {
      return getExecutingSlaves().indexOf(slaveState.getSlaveIndex());
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = StageResult.SUCCESS;
      logDurationInfo(acks);
      for (DistStageAck ack : acks) {
         if (ack.isError()) {
            log.warn("Received error ack " + ack);
            result = errorResult();
         } else {
            if (log.isTraceEnabled()) {
               log.trace("Received success ack " + ack);
            }
         }
      }
      return result;
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

   @Override
   public void initOnMaster(MasterState masterState) {
      this.masterState = masterState;
   }

   @Override
   public void initOnSlave(SlaveState slaveState) {
      this.slaveState = slaveState;
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
