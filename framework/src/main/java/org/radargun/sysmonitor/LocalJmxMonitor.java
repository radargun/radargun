package org.radargun.sysmonitor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXServiceURL;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static javax.management.remote.JMXConnectorFactory.connect;


/**
 * This program is designed to show how remote JMX calls can be used to retrieve metrics of remote JVMs. To monitor
 * other JVMs, make sure these are started with:
 * <p/>
 * -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false
 * -Dcom.sun.management.jmxremote.ssl=false
 * <p/>
 * And then simply subsitute the IP address in the JMX url by the IP address of the node.
 *
 * @author Galder Zamarreno
 */
public class LocalJmxMonitor {

   private String productName;
   private String configName;

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

   private static Log log = LogFactory.getLog(LocalJmxMonitor.class);
   public static final int MEASURING_FREQUENCY = 1000;

   private volatile CpuUsageMonitor cpuMonitor;
   private volatile MemoryUsageMonitor memoryMonitor;
   private volatile GcMonitor gcMonitor;

   final ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);

   public static void main(String[] args) throws Exception {
//      // If running in same VM...
//      final MBeanServerConnection con = ManagementFactory.getPlatformMBeanServer();

      // If remote access, start program with:
      // -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false
      final MBeanServerConnection con = connect(
            new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi"), null)
            .getMBeanServerConnection();

//      startMonitoringLocal(con);
      new CpuIntensiveTask().start();
   }

   public void startMonitoringLocal() {

      try {
         final MBeanServerConnection con = ManagementFactory.getPlatformMBeanServer();
         if (con == null)
            throw new IllegalStateException("PlatformMBeanServer not started!");

         cpuMonitor = new CpuUsageMonitor(con);
         exec.scheduleAtFixedRate(cpuMonitor, 0, 1000, TimeUnit.MILLISECONDS);
         memoryMonitor = new MemoryUsageMonitor(con);
         exec.scheduleAtFixedRate(memoryMonitor, 300, MEASURING_FREQUENCY, TimeUnit.MILLISECONDS);
         gcMonitor = new GcMonitor(con);
         exec.scheduleAtFixedRate(gcMonitor, 600, 1000, TimeUnit.MILLISECONDS);
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

   public void stopMonitoringLocal() {
      exec.shutdownNow();
      log.trace("Cpu measurements = " + cpuMonitor.getMeasurementCount() + ", memory measurements = "
                      + memoryMonitor.getMeasurementCount() + ", gc measurements = " + gcMonitor.getMeasurementCount());
   }

   static class CpuIntensiveTask extends Thread {
      @Override
      public void run() {
         while (true) {
            // If we garbage collect all the time, memory retrieval hangs
            // System.gc();
            byte[] b = new byte[1024];
         }
      }
   }

}
