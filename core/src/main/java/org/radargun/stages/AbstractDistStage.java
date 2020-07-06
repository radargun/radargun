package org.radargun.stages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.radargun.DistStage;
import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.helpers.RoleHelper;
import org.radargun.state.MainState;
import org.radargun.state.StateBase;
import org.radargun.state.WorkerState;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Lifecycle;
import org.radargun.utils.Utils;

/**
 * Support class for distributed stages.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Parent class for distributed stages.")
public abstract class AbstractDistStage extends AbstractStage implements DistStage {
   protected Log log = LogFactory.getLog(getClass());

   @Property(doc = "Specifies on which workers this stage should actively run. "
         + "The result set is intersection of specified workers, groups and roles. Default is all workers.")
   public Set<Integer> workers;

   @Property(doc = "Specifies in which groups this stage should actively run. "
         + "The result set is intersection of specified workers, groups and roles. Default is all groups.")
   public Set<String> groups;

   @Property(doc = "Specifies on which workers this stage should actively run, by their roles. "
         + "The result set is intersection of specified workers, groups and roles. " + "Supported roles are "
         + RoleHelper.SUPPORTED_ROLES + ". Default is all roles.")
   public Set<RoleHelper.Role> roles;

   @InjectTrait
   protected Lifecycle lifecycle;

   /**
    * This field is filled in only on main node, on worker it is set to null
    */
   protected MainState mainState;
   /**
    * This field is filled in only on worker node, on main it is set to null
    */
   protected WorkerState workerState;

   protected List<Integer> executingWorkers;

   public boolean isServiceRunning() {
      return lifecycle == null || lifecycle.isRunning();
   }

   @Override
   public boolean shouldExecute() {
      boolean execByWorker = workers == null || workers.contains(workerState.getWorkerIndex());
      boolean execByGroup = groups == null || groups.contains(workerState.getGroupName());
      boolean execByRole = roles == null || RoleHelper.hasAnyRole(workerState, roles);
      //Issue 425
      if (groups != null) {
         StateBase state = mainState != null ? mainState : workerState;
         if (state.getCluster() == null) {
            throw new IllegalStateException(String.format("Cluster has not been set in %s yet.", state));
         }
         for (String groupName : groups) {
            if (!state.getCluster().groupExists(groupName)) {
               throw new IllegalStateException(
                     String.format("Group '%s' does not exist in benchmark configuration.", groupName));
            }
         }
      }
      return execByWorker && execByGroup && execByRole;
   }

   /**
    * @return List of worker indices that are executing this stage (unless the workers/groups was
    *         evaluated differently on each worker). The order is guaranteed to be same across
    *         workers. Assumes cluster has been set in main/worker state.
    */
   protected List<Integer> getExecutingWorkers() {
      // Resolve according to invocation environment (main/worker)
      StateBase state = mainState != null ? mainState : workerState;
      if (state.getCluster() == null) {
         throw new IllegalStateException(String.format("Cluster has not been set in %s yet.", state));
      }
      if (executingWorkers != null) {
         return executingWorkers;
      }
      List<Integer> workers;
      if (this.workers == null) {
         workers = new ArrayList<>();
         for (int i = 0; i < state.getClusterSize(); ++i) {
            workers.add(i);
         }
      } else {
         workers = new ArrayList<>(this.workers);
         Collections.sort(workers);
      }
      if (groups != null) {
         for (Iterator<Integer> it = workers.iterator(); it.hasNext();) {
            int workerIndex = it.next();
            if (!groups.contains(state.getCluster().getGroup(workerIndex).name)) {
               it.remove();
            }
         }
      }
      // roles are not supported here as we can't determine if the worker has some role remotely
      return executingWorkers = workers;
   }

   protected int getExecutingWorkerIndex() {
      return getExecutingWorkers().indexOf(workerState.getWorkerIndex());
   }

   @Override
   public StageResult processAckOnMain(List<DistStageAck> acks) {
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
      if (!log.isInfoEnabled())
         return;

      String processingDuration = "Durations [";
      boolean first = true;
      for (DistStageAck ack : acks) {
         if (first)
            first = false;
         else
            processingDuration += ", ";
         processingDuration += ack.getWorkerIndex() + " = " + Utils.prettyPrintMillis(ack.getDuration());
      }
      log.info("Received responses from all " + acks.size() + " workers. " + processingDuration + "]");
   }

   @Override
   public void initOnMain(MainState mainState) {
      this.mainState = mainState;
   }

   @Override
   public Map<String, Object> createMainData() {
      return Collections.EMPTY_MAP;
   }

   @Override
   public void initOnWorker(WorkerState workerState) {
      this.workerState = workerState;
   }

   protected DistStageAck errorResponse(String message) {
      log.error(message);
      return new DistStageAck(workerState).error(message, null);
   }

   protected DistStageAck errorResponse(String message, Exception e) {
      log.error(message, e);
      return new DistStageAck(workerState).error(message, e);
   }

   protected DistStageAck successfulResponse() {
      return new DistStageAck(workerState);
   }
}
