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
package org.radargun.stages.helpers;

import java.util.Set;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.stages.cache.background.BackgroundOpsManager;
import org.radargun.state.SlaveState;
import org.radargun.traits.Clustered;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.Partitionable;

public class StartHelper {

   public static final String START_TIME = "START_TIME";

   private StartHelper() {}
   
   private static final Log log = LogFactory.getLog(StartHelper.class);
   
   public static void start(SlaveState slaveState, boolean validate, Integer expectedSlaves, long clusterFormationTimeout,
                            Set<Integer> reachable, DefaultDistStageAck ack) {
      Lifecycle lifecycle = slaveState.getTrait(Lifecycle.class);
      Clustered clustered = slaveState.getTrait(Clustered.class);
      Partitionable partitionable = slaveState.getTrait(Partitionable.class);
      try {
         if (partitionable != null) {
            partitionable.setStartWithReachable(slaveState.getSlaveIndex(), reachable);
         }

         long startingTime = System.nanoTime();
         lifecycle.start();
         long startedTime = System.nanoTime();
         ack.setPayload(StartStopTime.withStartTime(startedTime - startingTime, ack.getPayload()));
         if (validate && clustered != null) {
            
            int expectedNumberOfSlaves = expectedSlaves != null ? expectedSlaves : slaveState.getGroupSize();

            long clusterFormationDeadline = System.currentTimeMillis() + clusterFormationTimeout;
            for (;;) {
               int numMembers = clustered.getClusteredNodes();
               if (numMembers != expectedNumberOfSlaves) {
                  String msg = "Number of members=" + numMembers + " is not the one expected: " + expectedNumberOfSlaves;
                  log.info(msg);
                  Thread.sleep(1000);
                  if (System.currentTimeMillis() > clusterFormationDeadline) {
                     ack.setError(true);
                     ack.setErrorMessage(msg);
                     return;
                  }
               } else {
                  log.info("Number of members is the one expected: " + clustered.getClusteredNodes());
                  break;
               }
            }
         }
         if (lifecycle.isRunning()) {
            // here is a race so this is rather an optimization
            BackgroundOpsManager.afterCacheWrapperStart(slaveState);
         }
      } catch (Exception e) {
         log.error("Issues while instantiating/starting cache wrapper", e);
         ack.setError(true);
         ack.setRemoteException(e);
         if (lifecycle != null) {
            try {
               lifecycle.stop();
            } catch (Exception ignored) {
            }
         }
      }
   }
}
