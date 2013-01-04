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

import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.features.Partitionable;
import org.radargun.features.XSReplicating;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.state.SlaveState;
import org.radargun.stressors.BackgroundOpsManager;
import org.radargun.utils.ClassLoadHelper;
import org.radargun.utils.TypedProperties;
import org.radargun.utils.Utils;

public class StartHelper {
   
   private StartHelper() {}
   
   private static final Log log = LogFactory.getLog(StartHelper.class);
   
   private static final int TRY_COUNT = 180;
   
   public static class ClusterValidation {
      Integer expectedSlaves;
      int activeSlaveCount;
      
      public ClusterValidation(Integer expectedSlaves, int activeSlaveCount) {
         super();
         this.expectedSlaves = expectedSlaves;
         this.activeSlaveCount = activeSlaveCount;
      }
   }
   
   public static void start(String productName, String config, TypedProperties confAttributes, SlaveState slaveState, int slaveIndex,
         ClusterValidation clusterValidation, Set<Integer> reachable, ClassLoadHelper classLoadHelper, DefaultDistStageAck ack) {
      CacheWrapper wrapper = null;
      try {
         String plugin = getPluginWrapperClass(productName, confAttributes);         
         wrapper = (CacheWrapper) classLoadHelper.createInstance(plugin);
         if (wrapper instanceof Partitionable) {
            ((Partitionable) wrapper).setStartWithReachable(slaveIndex, reachable);
         }
         slaveState.setCacheWrapper(wrapper);
         wrapper.setUp(config, false, slaveIndex, confAttributes);
         if (clusterValidation != null) {
            
            int expectedNumberOfSlaves;
            if (clusterValidation.expectedSlaves != null) {
               expectedNumberOfSlaves = clusterValidation.expectedSlaves;
            } else if (wrapper instanceof XSReplicating) {
               List<Integer> slaves = ((XSReplicating) wrapper).getSlaves();
               expectedNumberOfSlaves = slaves != null ? slaves.size() : clusterValidation.activeSlaveCount;
            } else {
               expectedNumberOfSlaves = clusterValidation.activeSlaveCount;
            }
            
            for (int i = 0; i < TRY_COUNT; i++) {
               int numMembers = wrapper.getNumMembers();
               if (numMembers != expectedNumberOfSlaves) {
                  String msg = "Number of members=" + numMembers + " is not the one expected: " + expectedNumberOfSlaves;
                  log.info(msg);
                  Thread.sleep(1000);
                  if (i == TRY_COUNT - 1) {
                     ack.setError(true);
                     ack.setErrorMessage(msg);
                     return;
                  }
               } else {
                  log.info("Number of members is the one expected: " + wrapper.getNumMembers());
                  break;
               }
            }
         }
         if (wrapper.isRunning()) {
            // here is a race so this is rather an optimization
            BackgroundOpsManager.afterCacheWrapperStart(slaveState);
         }
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
      }
   }
   
   private static String getPluginWrapperClass(String productName, TypedProperties confAttributes) {
      if (confAttributes.getProperty("wrapper") != null) {
         return confAttributes.getProperty("wrapper");
      } else {
         return Utils.getCacheWrapperFqnClass(productName);
      }
   }
}
