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

import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
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

   public static final String MONITOR_KEY = "JVMMonitor";
   private TimeUnit timeUnit = TimeUnit.SECONDS;

   @Property(doc = "Specifies the network interface where statistics are gathered. "
         + "If not specified, then statistics are not collected.")
   private String interfaceName;

   @Property(doc = "Specifies the frequency that statistics are collected. "
         + "One of: SECONDS, MINUTES, or HOURS. The default is SECONDS.")
   private String timeUnitName = TimeUnit.SECONDS.name();

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      timeUnit = Enum.valueOf(TimeUnit.class, timeUnitName);
      LocalJmxMonitor monitor = new LocalJmxMonitor();
      monitor.setProductName(productName);
      monitor.setConfigName(configName);
      if (interfaceName != null) {
         monitor.setInterfaceName(interfaceName);
      }
      if (timeUnit != null) {
         monitor.setMeasuringUnit(timeUnit);
      } else {
         log.error("Failed to find time unit named " + timeUnitName);
      }
      monitor.startMonitoringLocal();
      slaveState.put(MONITOR_KEY, monitor);
      return ack;
   }
}
