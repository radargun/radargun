package org.radargun.stages;

import java.net.URLClassLoader;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.state.MasterState;
import org.radargun.stressors.BackgroundStats;
import org.radargun.utils.TypedProperties;
import org.radargun.utils.Utils;

/**
 * Stage that starts a CacheWrapper on each slave.
 * 
 * @author Mircea.Markus@jboss.com
 */
public class StartClusterStage extends AbstractDistStage {

   private boolean performClusterSizeValidation = true;
   private boolean staggerSlaveStartup = true;
   private long delayAfterFirstSlaveStarts = 5000;
   private long delayBetweenStartingSlaves = 500;
   private Integer expectNumSlaves;

   private String config;
   private final int TRY_COUNT = 180;

   private TypedProperties confAttributes;

   public StartClusterStage() {
      super.setExitBenchmarkOnSlaveFailure(true);
   }

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaveState.getCacheWrapper() != null) {
         log.info("Wrapper already set on this slave, not starting it again.");
         return ack;
      }
      if (slaves != null) {
         if (!slaves.contains(getSlaveIndex())) {
            log.trace("Start request not targeted for this slave, ignoring.");
            return ack;
         } else {
            staggerStartup(slaves.indexOf(getSlaveIndex()), slaves.size());
         }
      } else {
         staggerStartup(slaveIndex, getActiveSlaveCount());
      }
      log.info("Ack master's StartCluster stage. Local address is: " + slaveState.getLocalAddress()
            + ". This slave's index is: " + getSlaveIndex());
      CacheWrapper wrapper = null;
      try {
         String plugin = getPluginWrapperClass(confAttributes.get("multiCache"), confAttributes.get("partitions"));         
         wrapper = (CacheWrapper) createInstance(plugin);
         wrapper.setUp(config, false, slaveIndex, confAttributes);
         slaveState.setCacheWrapper(wrapper);
         if (performClusterSizeValidation) {
            int expectedNumberOfSlaves = expectNumSlaves == null ? getActiveSlaveCount() : expectNumSlaves;
            for (int i = 0; i < TRY_COUNT; i++) {
               int numMembers = wrapper.getNumMembers();
               if (numMembers != expectedNumberOfSlaves) {
                  String msg = "Number of members=" + numMembers + " is not the one expected: " + expectedNumberOfSlaves;
                  log.info(msg);
                  Thread.sleep(1000);
                  if (i == TRY_COUNT - 1) {
                     ack.setError(true);
                     ack.setErrorMessage(msg);
                     return ack;
                  }
               } else {
                  log.info("Number of members is the one expected: " + wrapper.getNumMembers());
                  break;
               }
            }
         }
         BackgroundStats.afterCacheWrapperStart(slaveState);
      } catch (Exception e) {
         log.error("Issues while instantiating/starting cache wrapper", e);
         ack.setError(true);
         ack.setRemoteException(e);
         if (wrapper != null) {
            try {
               wrapper.tearDown();
            } catch (Exception ignored) {
            }
         }
         return ack;
      }
      log.info("Successfully started cache wrapper on slave " + getSlaveIndex() + ": " + wrapper);
      return ack;
   }

   private String getPluginWrapperClass(Object multicache, Object partitions) {
      if (multicache != null && multicache.equals("true")) {
         return Utils.getCacheProviderProperty(productName, "org.radargun.wrapper.multicache");
      } else if (partitions != null && partitions.equals("true")) {
         return Utils.getCacheProviderProperty(productName, "org.radargun.wrapper.partitions");
      } else {
         return Utils.getCacheWrapperFqnClass(productName);
      }
   }

   public void setConfig(String config) {
      this.config = config;
   }


   public void setPerformCLusterSizeValidation(boolean performCLusterSizeValidation) {
      this.performClusterSizeValidation = performCLusterSizeValidation;
   }

   @Override
   public void initOnMaster(MasterState masterState, int slaveIndex) {
      super.initOnMaster(masterState, slaveIndex);
   }

   @Override
   public String toString() {
      return "StartClusterStage {config=" + config + ", " + super.toString();
   }

   public void setStaggerSlaveStartup(boolean staggerSlaveStartup) {
      this.staggerSlaveStartup = staggerSlaveStartup;
   }

   public void setDelayAfterFirstSlaveStarts(long delayAfterFirstSlaveStarts) {
      this.delayAfterFirstSlaveStarts = delayAfterFirstSlaveStarts;
   }

   public void setDelayBetweenStartingSlaves(long delayBetweenSlavesStarts) {
      this.delayBetweenStartingSlaves = delayBetweenSlavesStarts;
   }

   private void staggerStartup(int thisNodeIndex, int numSlavesToStart) {
      if (!staggerSlaveStartup) {
         if (log.isTraceEnabled()) {
            log.trace("Not using slave startup staggering");
         }
         return;
      }
      if (thisNodeIndex == 0) {
         log.info("Startup staggering, number of slaves to start is " + numSlavesToStart
               + " This is the slave with index 0, not sleeping");
         return;
      }
      long toSleep = delayAfterFirstSlaveStarts + thisNodeIndex * delayBetweenStartingSlaves;
      log.info(" Startup staggering, starting " + numSlavesToStart + " slaves. This is the slave with index "
            + thisNodeIndex + ". Sleeping for " + toSleep + " millis.");
      try {
         Thread.sleep(toSleep);
      } catch (InterruptedException e) {
         throw new IllegalStateException("Should never happen");
      }
   }

   public void setConfAttributes(TypedProperties confAttributes) {
      this.confAttributes = confAttributes;
   }

   public void setExpectNumSlaves(int numSlaves) {
      this.expectNumSlaves = numSlaves;
   }
   
}
