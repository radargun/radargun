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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.features.Killable;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.stages.KillStage;
import org.radargun.stages.ParallelStartKillStage;
import org.radargun.state.SlaveState;
import org.radargun.stressors.BackgroundStats;

/**
 * 
 * This class should execute node killing. Used in {@link KillStage} and {@link ParallelStartKillStage}.
 * Moved out as static because these can't be related through inheritance.  
 * 
 * @author Radim Vansa rvansa@redhat.com
 * @since September 2012
 */
public class KillHelper {
   
   private KillHelper() {}
  
   private static final Log log = LogFactory.getLog(KillHelper.class);
   
   public static void kill(SlaveState slaveState, boolean tearDown, boolean async, DefaultDistStageAck ack) {
      try {
         CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
         if (cacheWrapper != null) {
            BackgroundStats.beforeCacheWrapperDestroy(slaveState);
            if (tearDown) {
               log.info("Tearing down cache wrapper.");
               cacheWrapper.tearDown();
            } else if (cacheWrapper instanceof Killable) {
               log.info("Killing cache wrapper.");
               if (async) {
                  ((Killable) cacheWrapper).killAsync();
               } else {
                  ((Killable) cacheWrapper).kill();
               }
            } else {
               log.info("CacheWrapper is not killable, calling tearDown instead");
               cacheWrapper.tearDown();
            }
         } else {
            log.info("No cache wrapper deployed on this slave, nothing to do.");
         }
         slaveState.setCacheWrapper(null);
         // in case of concurrent start and kill the stats could be still running
         BackgroundStats.beforeCacheWrapperDestroy(slaveState);
      } catch (Exception e) {
         log.error("Error while killing slave", e);
         if (ack != null) {
            ack.setError(true);
            ack.setErrorMessage(e.getMessage());
            ack.setRemoteException(e);
         }
      } finally {
         System.gc();
      }
   }
}
