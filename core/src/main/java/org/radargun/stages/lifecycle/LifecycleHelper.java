/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
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

public class LifecycleHelper {

   protected static final String LIFECYCLE = "Lifecycle";

   private LifecycleHelper() {}
   
   private static final Log log = LogFactory.getLog(LifecycleHelper.class);
   
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
         try {
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
         } finally {
            for (ServiceListener listener : slaveState.getServiceListeners()) {
               listener.afterServiceStart();
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
