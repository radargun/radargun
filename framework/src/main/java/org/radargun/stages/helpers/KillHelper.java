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

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.stages.cache.background.BackgroundOpsManager;
import org.radargun.stages.lifecycle.KillStage;
import org.radargun.stages.lifecycle.ParallelStartKillStage;
import org.radargun.state.SlaveState;
import org.radargun.traits.Killable;
import org.radargun.traits.Lifecycle;

/**
 * 
 * This class should execute node killing. Used in {@link KillStage} and {@link ParallelStartKillStage}.
 * Moved out as static because these can't be related through inheritance.  
 * 
 * @author Radim Vansa rvansa@redhat.com
 * @since September 2012
 */
//TODO: merge StartHelper and KillHelper -> LifecycleHelper
public class KillHelper {

   private KillHelper() {}
  
   private static final Log log = LogFactory.getLog(KillHelper.class);
   
   public static void kill(SlaveState slaveState, boolean graceful, boolean async) {
      Lifecycle lifecycle = slaveState.getTrait(Lifecycle.class);
      if (lifecycle == null) throw new IllegalArgumentException();
      Killable killable = slaveState.getTrait(Killable.class);
      if (lifecycle.isRunning()) {
         BackgroundOpsManager.beforeCacheWrapperDestroy(slaveState, false);
         long stoppingTime;
         if (graceful) {
            log.info("Stopping service.");
            stoppingTime = System.currentTimeMillis();
            lifecycle.stop();
         } else if (killable != null) {
            log.info("Killing service.");
            stoppingTime = System.currentTimeMillis();
            if (async) {
               killable.killAsync();
            } else {
               killable.kill();
            }
         } else {
            log.info("Service is not Killable, stopping instead");
            stoppingTime = System.currentTimeMillis();
            lifecycle.stop();
         }
         long stoppedTime = System.currentTimeMillis();
         slaveState.getTimeline().addEvent(StartHelper.LIFECYCLE, new Timeline.IntervalEvent(stoppingTime, "Stop", stoppedTime - stoppingTime));
      } else {
         log.info("No cache wrapper deployed on this slave, nothing to do.");
      }
      // in case of concurrent start and kill the stats could be still running
      BackgroundOpsManager.beforeCacheWrapperDestroy(slaveState, false);
      System.gc();
   }
}
