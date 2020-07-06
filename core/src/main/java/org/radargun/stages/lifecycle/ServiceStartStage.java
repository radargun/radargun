package org.radargun.stages.lifecycle;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.WorkerState;
import org.radargun.traits.ConfigurationProvider;
import org.radargun.traits.InjectTrait;
import org.radargun.utils.TimeConverter;

/**
 * Stage that starts a CacheWrapper on each worker.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Starts services on specified workers")
public class ServiceStartStage extends AbstractServiceStartStage {

   @Property(doc = "Specifies whether the cluster formation should be checked after cache wrapper startup. Default is true.")
   private boolean validateCluster = true;

   @Property(doc = "If set to true, the workers will not be started in one moment but the startup will be delayed. Default is true.")
   private boolean staggerWorkerStartup = true;

   @Property(converter = TimeConverter.class, doc = "Delay (staggering) after first worker's start is initiated. Default is 5s.")
   private long delayAfterFirstWorkerStarts = 5000;

   @Property(converter = TimeConverter.class, doc = "Delay between initiating start of i-th and (i+1)-th worker. Default is 500 ms.")
   private long delayBetweenStartingWorkers = 500;

   @Property(converter = TimeConverter.class, doc = "Time allowed the cluster to reach `expectNumWorkers` members. Default is 3 minutes.")
   private long clusterFormationTimeout = 180000;

   @Property(doc = "Collect configuration files and properties for the service, and pass those to reporters. Default is true.")
   private boolean dumpConfig = true;

   @Property(doc = "The number of members that should be up after all services are started. Applicable only with "
         + "validateCluster=true. Default is all members in the group where this stage will be executed. (If no "
         + "groups are configured, then this is equal to all members of the cluster.) If multiple groups are"
         + "specified in the benchmark, then the size of each group will considered separately.")
   private Integer expectNumWorkers;

   @Property(doc = "Set of workers that should be reachable to the newly spawned workers (see Partitionable feature for details). Default is all workers.")
   private Set<Integer> reachable = null;

   @InjectTrait
   private ConfigurationProvider configurationProvider;

   public DistStageAck executeOnWorker() {
      if (!shouldExecute()) {
         log.trace("Start request not targeted for this worker, ignoring.");
         return successfulResponse();
      } else if (lifecycle == null) {
         log.warn("Service " + workerState.getServiceName() + " does not support lifecycle management.");
         return successfulResponse();
      } else if (lifecycle.isRunning()) {
         log.info("Service " + workerState.getServiceName() + " is already running.");
         return successfulResponse();
      }

      int index = 0;
      for (Integer worker : getExecutingWorkers()) {
         if (worker.equals(workerState.getWorkerIndex())) break;
         index++;
      }
      staggerStartup(index);

      log.info("Ack main's StartCluster stage. Local address is: " + workerState.getLocalAddress()
         + ". This worker's index is: " + workerState.getWorkerIndex());

      // If no value of expectNumWorkers is supplied, then use the worker's group size as the default
      if (expectNumWorkers == null) {
         Set<Integer> group = workerState.getCluster().getWorkers(workerState.getGroupName());
         group.retainAll(getExecutingWorkers());
         expectNumWorkers = group.size();
      }

      try {
         LifecycleHelper.start(workerState, validateCluster, expectNumWorkers, clusterFormationTimeout, reachable);
      } catch (RuntimeException e) {
         return errorResponse("Issues while instantiating/starting service", e);
      }
      log.info("Successfully started cache service " + workerState.getServiceName() + " on worker " + workerState.getWorkerIndex());
      if (configurationProvider != null && dumpConfig) {
         return new ServiceStartAck(workerState, configurationProvider.getNormalizedConfigs(), configurationProvider.getOriginalConfigs());
      } else {
         return new ServiceStartAck(workerState, Collections.EMPTY_MAP, Collections.EMPTY_MAP);
      }
   }

   private void staggerStartup(int thisNodeIndex) {
      if (!staggerWorkerStartup) {
         if (log.isTraceEnabled()) {
            log.trace("Not using worker startup staggering");
         }
         return;
      }
      if (thisNodeIndex == 0) {
         log.info("Startup staggering, this is the worker with index 0, not sleeping");
         return;
      }
      long toSleep = delayAfterFirstWorkerStarts + thisNodeIndex * delayBetweenStartingWorkers;
      log.info(" Startup staggering, this is the worker with index "
         + thisNodeIndex + ". Sleeping for " + toSleep + " millis.");
      try {
         Thread.sleep(toSleep);
      } catch (InterruptedException e) {
         throw new IllegalStateException("Should never happen");
      }
   }

   public StageResult processAckOnMain(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMain(acks);
      if (result.isError()) return result;

      if (dumpConfig) {
         for (DistStageAck ack : acks) {
            if (ack instanceof ServiceStartAck) {
               ServiceStartAck sAck = (ServiceStartAck) ack;
               mainState.getReport().addNormalizedServiceConfig(sAck.getWorkerIndex(), sAck.gerNormalizedConfigs());
               mainState.getReport().addOriginalServiceConfig(sAck.getWorkerIndex(), sAck.getOriginalConfigs());
            }
         }
      }
      return StageResult.SUCCESS;
   }

   public static class ServiceStartAck extends DistStageAck {

      private Map<String, Properties> normalizedConfigs;
      private Map<String, byte[]> originalConfigs;

      private ServiceStartAck(WorkerState workerState, Map<String, Properties> normalizedConfigs, Map<String, byte[]> originalConfigs) {
         super(workerState);
         this.normalizedConfigs = normalizedConfigs;
         this.originalConfigs = originalConfigs;
      }

      public Map<String, Properties> gerNormalizedConfigs() {
         return normalizedConfigs;
      }

      public Map<String, byte[]> getOriginalConfigs() {
         return originalConfigs;
      }
   }
}
