package org.radargun.sysmonitor;

import static javax.management.remote.JMXConnectorFactory.connect;

import java.io.Serializable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


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
public class LocalJmxMonitor implements Serializable {

   /** The serialVersionUID */
   private static final long serialVersionUID = 2530981300271084693L;
   
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
//   private MBeanServerConnection con;

   ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);

   //todo remove these lines once remote connection is available..
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
         cpuMonitor = new CpuUsageMonitor();
         exec.scheduleAtFixedRate(cpuMonitor, 0, MEASURING_FREQUENCY, TimeUnit.MILLISECONDS);
         memoryMonitor = new MemoryUsageMonitor();
         exec.scheduleAtFixedRate(memoryMonitor, 0, MEASURING_FREQUENCY, TimeUnit.MILLISECONDS);
         gcMonitor = new GcMonitor();
         exec.scheduleAtFixedRate(gcMonitor, 0, MEASURING_FREQUENCY, TimeUnit.MILLISECONDS);
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
      cpuMonitor.stop();
      memoryMonitor.stop();
      gcMonitor.stop();
      exec.shutdownNow();
      this.exec = null;
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
