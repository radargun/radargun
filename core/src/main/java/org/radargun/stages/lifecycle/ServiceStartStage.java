package org.radargun.stages.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.utils.TimeConverter;
import org.radargun.state.SlaveState;
import org.radargun.traits.ConfigurationProvider;
import org.radargun.traits.InjectTrait;

/**
 * Stage that starts a CacheWrapper on each slave.
 * 
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Starts services on specified slaves")
public class ServiceStartStage extends AbstractServiceStartStage {

   @Property(doc = "Specifies whether the cluster formation should be checked after cache wrapper startup. Default is true.")
   private boolean validateCluster = true;

   @Property(doc = "If set to true, the slaves will not be started in one moment but the startup will be delayed. Default is true.")
   private boolean staggerSlaveStartup = true;

   @Property(converter = TimeConverter.class, doc = "Delay (staggering) after first slave's start is initiated. Default is 5s")
   private long delayAfterFirstSlaveStarts = 5000;

   @Property(converter = TimeConverter.class, doc = "Delay between initiating start of i-th and (i+1)-th slave. Default is 500 ms")
   private long delayBetweenStartingSlaves = 500;

   @Property(converter = TimeConverter.class, doc = "Time allowed the cluster to reach `expectNumSlaves` members. Default is 3 minutes.")
   private long clusterFormationTimeout = 180000;

   @Property(doc = "Collect configuration files and properties for the service, and pass those to reporters. Default is true.")
   private boolean dumpConfig = true;

   @Property(doc = "The number of slaves that should be up after all slaves are started. Applicable only with " +
         "validateCluster=true. Default is all slaves in the cluster (in the same site in case of multi-site configuration).")
   private Integer expectNumSlaves;

   @Property(doc = "Set of slaves that should be reachable to the newly spawned slaves (see Partitionable feature for details). Default is all slaves.")
   private Set<Integer> reachable = null;

   @InjectTrait
   private ConfigurationProvider configurationProvider;

   public DistStageAck executeOnSlave() {
      if (!shouldExecute()) {
         log.trace("Start request not targeted for this slave, ignoring.");
         return successfulResponse();
      } else if (lifecycle == null) {
         log.warn("Service " + slaveState.getServiceName() + " does not support lifecycle management.");
         return successfulResponse();
      } else if (lifecycle.isRunning()) {
         log.info("Service " + slaveState.getServiceName() + " is already running.");
         return successfulResponse();
      }

      int index = 0;
      for (Integer slave : getExecutingSlaves()) {
         if (slave.equals(slaveState.getSlaveIndex())) break;
         index++;
      }
      staggerStartup(index);

      log.info("Ack master's StartCluster stage. Local address is: " + slaveState.getLocalAddress()
            + ". This slave's index is: " + slaveState.getSlaveIndex());
      try {
         LifecycleHelper.start(slaveState, validateCluster, expectNumSlaves, clusterFormationTimeout, reachable);
      } catch (RuntimeException e) {
         return errorResponse("Issues while instantiating/starting cache wrapper", e);
      }
      log.info("Successfully started cache service " + slaveState.getServiceName() + " on slave " + slaveState.getSlaveIndex());
      if (configurationProvider != null && dumpConfig) {
         return new ServiceStartAck(slaveState, configurationProvider.getNormalizedConfigs(), configurationProvider.getOriginalConfigs());
      } else {
         return new ServiceStartAck(slaveState, null, null);
      }
   }

   private void staggerStartup(int thisNodeIndex) {
      if (!staggerSlaveStartup) {
         if (log.isTraceEnabled()) {
            log.trace("Not using slave startup staggering");
         }
         return;
      }
      if (thisNodeIndex == 0) {
         log.info("Startup staggering, this is the slave with index 0, not sleeping");
         return;
      }
      long toSleep = delayAfterFirstSlaveStarts + thisNodeIndex * delayBetweenStartingSlaves;
      log.info(" Startup staggering, this is the slave with index "
            + thisNodeIndex + ". Sleeping for " + toSleep + " millis.");
      try {
         Thread.sleep(toSleep);
      } catch (InterruptedException e) {
         throw new IllegalStateException("Should never happen");
      }
   }

   public boolean processAckOnMaster(List<DistStageAck> acks) {
      boolean result = super.processAckOnMaster(acks);
      if (dumpConfig) {
         for (DistStageAck ack : acks) {
            if (ack instanceof ServiceStartAck) {
               ServiceStartAck sAck = (ServiceStartAck) ack;
               masterState.getReport().addNormalizedServiceConfig(sAck.getSlaveIndex(), sAck.gerNormalizedConfigs());
               masterState.getReport().addOriginalServiceConfig(sAck.getSlaveIndex(), sAck.getOriginalConfigs());
            }
         }
      }
      return result;
   }

   public static class ServiceStartAck extends DistStageAck {

      private Map<String, Properties> normalizedConfigs;
      private Map<String, byte[]> originalConfigs;

      private ServiceStartAck(SlaveState slaveState, Map<String, Properties> normalizedConfigs, Map<String, byte[]> originalConfigs) {
         super(slaveState);
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
