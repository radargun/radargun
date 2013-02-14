/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
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

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.state.MasterState;
import org.radargun.sysmonitor.LocalJmxMonitor;

/**
 * 
 * Starts collecting JVM statistics locally on each slave node.
 * {@link LocalJmxMonitor}
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */

@Stage(doc = "Starts collecting JVM statistics locally on each slave node.")
public class StartJVMMonitorStage extends AbstractDistStage {

   public static final String monitorKey = "JVMMonitor";
   private String productName;
   private String configName;

   @Override
   public void initOnMaster(MasterState masterState, int slaveIndex) {
      productName = masterState.nameOfTheCurrentBenchmark();
      configName = masterState.configNameOfTheCurrentBenchmark();
   }

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      LocalJmxMonitor monitor = new LocalJmxMonitor();
      monitor.setProductName(productName);
      monitor.setConfigName(configName);
      monitor.startMonitoringLocal();
      slaveState.put(monitorKey, monitor);
      return ack;
   }
}
