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
package org.radargun.sysmonitor;

import java.io.Serializable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This program is designed to show how remote JMX calls can be used to retrieve metrics of remote
 * JVMs. To monitor other JVMs, make sure these are started with:
 * <p/>
 * -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false
 * -Dcom.sun.management.jmxremote.ssl=false
 * <p/>
 * And then simply subsitute the IP address in the JMX url by the IP address of the node.
 * 
 * @author Galder Zamarreno
 */
public class LocalJmxMonitor implements Serializable {

   /** The serialVersionUID */
   private static final long serialVersionUID = 2530981300271084693L;

   private String productName;
   private String configName;
   private String interfaceName;

   public String getProductName() {
      return productName;
   }

   public void setProductName(String productName) {
      this.productName = productName;
   }

   public String getConfigName() {
      return configName;
   }

   public void setConfigName(String configName) {
      this.configName = configName;
   }

   public String getInterfaceName() {
      return this.interfaceName;
   }

   public void setInterfaceName(String interfaceName) {
      this.interfaceName = interfaceName;
   }

   private static Log log = LogFactory.getLog(LocalJmxMonitor.class);
   public static final int MEASURING_FREQUENCY = 1000;

   private volatile CpuUsageMonitor cpuMonitor;
   private volatile MemoryUsageMonitor memoryMonitor;
   private volatile GcMonitor gcMonitor;
   private volatile NetworkBytesInMonitor netInMonitor;
   private volatile NetworkBytesOutMonitor netOutMonitor;

   ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);

   public void startMonitoringLocal() {

      try {
         cpuMonitor = new CpuUsageMonitor();
         exec.scheduleAtFixedRate(cpuMonitor, 0, MEASURING_FREQUENCY, TimeUnit.MILLISECONDS);
         memoryMonitor = new MemoryUsageMonitor();
         exec.scheduleAtFixedRate(memoryMonitor, 0, MEASURING_FREQUENCY, TimeUnit.MILLISECONDS);
         gcMonitor = new GcMonitor();
         exec.scheduleAtFixedRate(gcMonitor, 0, MEASURING_FREQUENCY, TimeUnit.MILLISECONDS);
         if (interfaceName != null) {
            netInMonitor = new NetworkBytesInMonitor(interfaceName);
            exec.scheduleAtFixedRate(netInMonitor, 0, MEASURING_FREQUENCY, TimeUnit.MILLISECONDS);
            netOutMonitor = new NetworkBytesOutMonitor(interfaceName);
            exec.scheduleAtFixedRate(netOutMonitor, 0, MEASURING_FREQUENCY, TimeUnit.MILLISECONDS);
         }
      } catch (Exception e) {
         log.error(e.getMessage(), e);
      }
   }

   public CpuUsageMonitor getCpuMonitor() {
      return cpuMonitor;
   }

   public MemoryUsageMonitor getMemoryMonitor() {
      return memoryMonitor;
   }

   public GcMonitor getGcMonitor() {
      return gcMonitor;
   }

   public NetworkBytesInMonitor getNetworkBytesInMonitor() {
      return netInMonitor;
   }

   public NetworkBytesOutMonitor getNetworkBytesOutMonitor() {
      return netOutMonitor;
   }

   public void stopMonitoringLocal() {
      cpuMonitor.stop();
      memoryMonitor.stop();
      gcMonitor.stop();
      if (interfaceName != null) {
         netInMonitor.stop();
         netOutMonitor.stop();
      }
      exec.shutdownNow();
      this.exec = null;
      StringBuffer result = new StringBuffer("Cpu measurements = " + cpuMonitor.getMeasurementCount() + ", memory measurements = "
            + memoryMonitor.getMeasurementCount() + ", gc measurements = " + gcMonitor.getMeasurementCount());
      if (interfaceName != null) {
         result.append(", network inbound measurements = " + netInMonitor.getMeasurementCount());
         result.append(", network outbound measurements = " + netOutMonitor.getMeasurementCount());
      }
      log.trace(result.toString());
   }
}
