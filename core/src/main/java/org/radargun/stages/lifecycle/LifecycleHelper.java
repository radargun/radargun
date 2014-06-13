package org.radargun.stages.lifecycle;

import java.util.Set;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.state.ServiceListener;
import org.radargun.state.SlaveState;
import org.radargun.traits.Clustered;
import org.radargun.traits.Killable;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.Partitionable;

/**
 * Helper class for controlling the lifecycle of a service.
 * Assumes that the service has the {@link Lifecycle} trait,
 * other traits are optional.
 */
public class LifecycleHelper {

   protected static final String LIFECYCLE = "Lifecycle";

   private LifecycleHelper() {}
   
   private static final Log log = LogFactory.getLog(LifecycleHelper.class);

   /**
    * Starts the service.
    * If the service supports {@link Clustered} trait and {@code validate} is set to true,
    * the method waits until {@link org.radargun.traits.Clustered#getClusteredNodes() clustered trait}
    * reports {@code expectedSlaves} slaves or for {@code clusterFormationTimeout} milliseconds.
    * If the service supports {@link Partitionable} trait, the set of {@code reachable} slaves
    * is set up before the service is started.
    * Also, this method calls the {@link ServiceListener service listeners} on {@link SlaveState}.
    * If the start fails, attempt to stop the service is executed.
    *
    * @param slaveState
    * @param validate
    * @param expectedSlaves
    * @param clusterFormationTimeout
    * @param reachable
    */
   public static void start(SlaveState slaveState, boolean validate, Integer expectedSlaves, long clusterFormationTimeout,
                            Set<Integer> reachable) {
      Lifecycle lifecycle = slaveState.getTrait(Lifecycle.class);
      Clustered clustered = slaveState.getTrait(Clustered.class);
      Partitionable partitionable = slaveState.getTrait(Partitionable.class);
      try {
         if (partitionable != null) {
            partitionable.setStartWithReachable(slaveState.getSlaveIndex(), reachable);
         }
         for (ServiceListener listener : slaveState.getServiceListeners()) {
            listener.beforeServiceStart();
         }
         long startingTime = System.currentTimeMillis();
         lifecycle.start();
         long startedTime = System.currentTimeMillis();
         slaveState.getTimeline().addEvent(LifecycleHelper.LIFECYCLE, new Timeline.IntervalEvent(startingTime, "Start", startedTime - startingTime));
         if (validate && clustered != null) {

            int expectedNumberOfSlaves = expectedSlaves != null ? expectedSlaves : slaveState.getGroupSize();

            long clusterFormationDeadline = System.currentTimeMillis() + clusterFormationTimeout;
            for (;;) {
               int numMembers = clustered.getClusteredNodes();
               if (numMembers != expectedNumberOfSlaves) {
                  String msg = "Number of members=" + numMembers + " is not the one expected: " + expectedNumberOfSlaves;
                  log.info(msg);
                  try {
                     Thread.sleep(1000);
                  } catch (InterruptedException ie) {
                     Thread.currentThread().interrupt();
                  }
                  if (System.currentTimeMillis() > clusterFormationDeadline) {
                     throw new ClusterFormationTimeoutException(msg);
                  }
               } else {
                  log.info("Number of members is the one expected: " + clustered.getClusteredNodes());
                  break;
               }
            }
         }
         for (ServiceListener listener : slaveState.getServiceListeners()) {
            try {
               listener.afterServiceStart();
            } catch (Exception e) {
               log.error("Failed to run listener " + listener, e);
            }
         }
      } catch (RuntimeException e) {
         log.trace("Failed to start", e);
         try {
            lifecycle.stop();
         } catch (Exception ignored) {
            log.trace("Failed to stop after start failed", ignored);
         }
         throw e;
      }
   }

   /**
    * Stops the service.
    * If the service supports the {@link Killable} trait and {@code graceful} is set to false,
    * this trait is used to kill the service instead of stopping it. Also, non-graceful stop
    * can be executed asynchronously using {@link org.radargun.traits.Killable#killAsync()}.
    * This method calls the {@link ServiceListener service listeners} on {@link SlaveState}.
    *
    * @param slaveState
    * @param graceful
    * @param async
    */
   public static void stop(SlaveState slaveState, boolean graceful, boolean async) {
      Lifecycle lifecycle = slaveState.getTrait(Lifecycle.class);
      if (lifecycle == null) throw new IllegalArgumentException();
      Killable killable = slaveState.getTrait(Killable.class);
      if (lifecycle.isRunning()) {
         for (ServiceListener listener : slaveState.getServiceListeners()) {
            listener.beforeServiceStop(graceful);
         }
         try {
            long stoppingTime;
            if (graceful || killable == null) {
               if (async) {
                  log.warn("Async graceful stop is not supported.");
               }
               if (graceful) {
                  log.info("Stopping service.");
               } else {
                  log.info("Service is not Killable, stopping instead");
               }
               stoppingTime = System.currentTimeMillis();
               lifecycle.stop();
            } else {
               log.info("Killing service.");
               stoppingTime = System.currentTimeMillis();
               if (async) {
                  killable.killAsync();
               } else {
                  killable.kill();
               }
            }
            long stoppedTime = System.currentTimeMillis();
            slaveState.getTimeline().addEvent(LIFECYCLE, new Timeline.IntervalEvent(stoppingTime, "Stop", stoppedTime - stoppingTime));
         } finally {
            for (ServiceListener listener : slaveState.getServiceListeners()) {
               listener.afterServiceStop(graceful);
            }
         }
      } else {
         log.info("No cache wrapper deployed on this slave, nothing to do.");
      }
   }

   private static class ClusterFormationTimeoutException extends RuntimeException {
      public ClusterFormationTimeoutException(String msg) {
         super(msg);
      }
   }
}
