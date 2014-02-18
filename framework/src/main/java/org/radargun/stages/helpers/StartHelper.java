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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.CacheWrapper;
import org.radargun.config.DefaultConverter;
import org.radargun.config.PropertyHelper;
import org.radargun.features.Partitionable;
import org.radargun.features.XSReplicating;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.state.SlaveState;
import org.radargun.stressors.BackgroundOpsManager;
import org.radargun.utils.Utils;

public class StartHelper {

   public static final String START_TIME = "START_TIME";

   private StartHelper() {}
   
   private static final Log log = LogFactory.getLog(StartHelper.class);
   
   public static class ClusterValidation {
      Integer expectedSlaves;
      int activeSlaveCount;
      
      public ClusterValidation(Integer expectedSlaves, int activeSlaveCount) {
         super();
         this.expectedSlaves = expectedSlaves;
         this.activeSlaveCount = activeSlaveCount;
      }
   }
   
   public static void start(SlaveState slaveState, String service, Map<String, String> configProperties,
                            ClusterValidation clusterValidation, long clusterFormationTimeout,
                            Set<Integer> reachable, DefaultDistStageAck ack) {
      CacheWrapper control = null;
      try {
         String controlClass = Utils.getServiceProperty(slaveState.getPlugin(), "service." + service);
         control = (CacheWrapper) slaveState.getClassLoadHelper().createInstance(controlClass);
         PropertyHelper.setProperties(control, configProperties, true, true);
         if (control instanceof Partitionable) {
            ((Partitionable) control).setStartWithReachable(slaveState.getSlaveIndex(), reachable);
         }
         slaveState.setCacheWrapper(control);
         long startingTime = System.nanoTime();
         control.setUp(false, slaveState.getSlaveIndex());
         long startedTime = System.nanoTime();
         ack.setPayload(StartStopTime.withStartTime(startedTime - startingTime, ack.getPayload()));
         if (clusterValidation != null) {
            
            int expectedNumberOfSlaves;
            if (clusterValidation.expectedSlaves != null) {
               expectedNumberOfSlaves = clusterValidation.expectedSlaves;
            } else if (control instanceof XSReplicating) {
               List<Integer> slaves = ((XSReplicating) control).getSlaves();
               expectedNumberOfSlaves = slaves != null ? slaves.size() : clusterValidation.activeSlaveCount;
            } else {
               expectedNumberOfSlaves = clusterValidation.activeSlaveCount;
            }

            long clusterFormationDeadline = System.currentTimeMillis() + clusterFormationTimeout;
            for (;;) {
               int numMembers = control.getNumMembers();
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
                  log.info("Number of members is the one expected: " + control.getNumMembers());
                  break;
               }
            }
         }
         if (control.isRunning()) {
            // here is a race so this is rather an optimization
            BackgroundOpsManager.afterCacheWrapperStart(slaveState);
         }
      } catch (Exception e) {
         log.error("Issues while instantiating/starting cache wrapper", e);
         ack.setError(true);
         ack.setRemoteException(e);
         if (control != null) {
            try {
               control.tearDown();
            } catch (Exception ignored) {
            }
         }
      }
   }

   @Deprecated
   private static Map<String, String> pickForSite(Map<String, String> configProperties, int slaveIndex) {
      Pattern slavesPattern = Pattern.compile("site\\[(\\d*)\\].slaves");
      Matcher m;
      int mySiteIndex = -1;
      Map<String, String> properties = new HashMap<String, String>();
      for (Map.Entry<String, String> entry : configProperties.entrySet()) {
         if ((m = slavesPattern.matcher(entry.getKey())).matches()) {
            List<Integer> slaves = (List<Integer>) DefaultConverter.staticConvert(entry.getValue(), DefaultConverter.parametrized(List.class, Integer.class));
            properties.put("slaves", entry.getValue());
            if (slaves.contains(slaveIndex)) {
               if (mySiteIndex >= 0) throw new IllegalArgumentException("Slave set up in multiple sites!");
               try {
                  mySiteIndex = Integer.parseInt(m.group(1));
               } catch (NumberFormatException e) {
                  log.debug("Cannot parse site index from " + entry.getKey());
               }
            }
         } else if (!entry.getKey().startsWith("site[")) {
            properties.put(entry.getKey(), entry.getValue());
         }
      }
      // site properties override global ones
      if (mySiteIndex >= 0) {
         properties.put("siteIndex", String.valueOf(mySiteIndex));
         String prefix = "site[" + mySiteIndex + "].";
         for (Map.Entry<String, String> entry: configProperties.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
               if (entry.getKey().equalsIgnoreCase(prefix + "name")) {
                  properties.put("siteName", entry.getValue());
               } else {
                  properties.put(entry.getKey().substring(prefix.length()), entry.getValue());
               }
            }
         }
      }
      return properties;
   }
}
