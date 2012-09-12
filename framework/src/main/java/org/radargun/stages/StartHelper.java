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
package org.radargun.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.state.SlaveState;
import org.radargun.stressors.BackgroundStats;
import org.radargun.utils.ClassLoadHelper;
import org.radargun.utils.TypedProperties;
import org.radargun.utils.Utils;

public class StartHelper {
   
   private static final Log log = LogFactory.getLog(StartHelper.class);
   
   private static final int TRY_COUNT = 180;
   
   public static void start(String productName, String config, TypedProperties confAttributes, SlaveState slaveState, int slaveIndex,
         boolean performClusterSizeValidation, int expectedNumberOfSlaves, ClassLoadHelper classLoadHelper, DefaultDistStageAck ack) {
      CacheWrapper wrapper = null;
      try {
         String plugin = getPluginWrapperClass(productName, confAttributes.get("multiCache"), confAttributes.get("partitions"));         
         wrapper = (CacheWrapper) classLoadHelper.createInstance(plugin);
         wrapper.setUp(config, false, slaveIndex, confAttributes);
         slaveState.setCacheWrapper(wrapper);
         if (performClusterSizeValidation) {            
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
      }
   }
   
   private static String getPluginWrapperClass(String productName, Object multicache, Object partitions) {
      if (multicache != null && multicache.equals("true")) {
         return Utils.getCacheProviderProperty(productName, "org.radargun.wrapper.multicache");
      } else if (partitions != null && partitions.equals("true")) {
         return Utils.getCacheProviderProperty(productName, "org.radargun.wrapper.partitions");
      } else {
         return Utils.getCacheWrapperFqnClass(productName);
      }
   }
}
