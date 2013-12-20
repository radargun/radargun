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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.config.DefaultConverter;
import org.radargun.features.Partitionable;
import org.radargun.features.XSReplicating;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.state.SlaveState;
import org.radargun.stressors.BackgroundOpsManager;
import org.radargun.utils.ClassLoadHelper;
import org.radargun.utils.TypedProperties;
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
   
   public static void start(String productName, String config, TypedProperties confAttributes, SlaveState slaveState, int slaveIndex,
                            ClusterValidation clusterValidation, long clusterFormationTimeout, Set<Integer> reachable, ClassLoadHelper classLoadHelper, DefaultDistStageAck ack) {
      CacheWrapper wrapper = null;
      try {
         confAttributes = pickForSite(confAttributes, slaveIndex);
         String plugin = getPluginWrapperClass(productName, confAttributes);         
         wrapper = (CacheWrapper) classLoadHelper.createInstance(plugin);
         if (wrapper instanceof Partitionable) {
            ((Partitionable) wrapper).setStartWithReachable(slaveIndex, reachable);
         }
         slaveState.setCacheWrapper(wrapper);
         long startingTime = System.nanoTime();
         wrapper.setUp(config, false, slaveIndex, confAttributes);
         long startedTime = System.nanoTime();
         ack.setPayload(StartStopTime.withStartTime(startedTime - startingTime, ack.getPayload()));
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

            long clusterFormationDeadline = System.currentTimeMillis() + clusterFormationTimeout;
            for (;;) {
               int numMembers = wrapper.getNumMembers();
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

   private static TypedProperties pickForSite(TypedProperties confAttributes, int slaveIndex) {
      Pattern slavesPattern = Pattern.compile("site\\[(\\d*)\\].slaves");
      Matcher m;
      int mySiteIndex = -1;
      TypedProperties properties = new TypedProperties();
      for (String property : confAttributes.stringPropertyNames()) {
         if ((m = slavesPattern.matcher(property)).matches()) {
            String value = confAttributes.getProperty(property);
            List<Integer> slaves = (List<Integer>) DefaultConverter.staticConvert(value, DefaultConverter.parametrized(List.class, Integer.class));
            properties.setProperty("slaves", value);
            if (slaves.contains(slaveIndex)) {
               if (mySiteIndex >= 0) throw new IllegalArgumentException("Slave set up in multiple sites!");
               try {
                  mySiteIndex = Integer.parseInt(m.group(1));
               } catch (NumberFormatException e) {
                  log.debug("Cannot parse site index from " + property);
               }
            }
         } else if (!property.startsWith("site[")) {
            properties.setProperty(property, confAttributes.getProperty(property));
         }
      }
      // site properties override global ones
      if (mySiteIndex >= 0) {
         properties.setProperty("siteIndex", String.valueOf(mySiteIndex));
         String prefix = "site[" + mySiteIndex + "].";
         for (String property: confAttributes.stringPropertyNames()) {
            if (property.startsWith(prefix)) {
               if (property.equalsIgnoreCase(prefix + "name")) {
                  properties.setProperty("siteName", confAttributes.getProperty(prefix + "name"));
               } else {
                  properties.setProperty(property.substring(prefix.length()), confAttributes.getProperty(property));
               }
            }
         }
      }
      return properties;
   }

   private static String getPluginWrapperClass(String productName, TypedProperties confAttributes) {
      if (confAttributes.getProperty("wrapper") != null) {
         return confAttributes.getProperty("wrapper");
      } else {
         return Utils.getCacheWrapperFqnClass(productName);
      }
   }
}
