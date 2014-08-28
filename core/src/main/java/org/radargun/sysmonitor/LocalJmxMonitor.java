package org.radargun.sysmonitor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.state.ServiceListener;
import org.radargun.state.SlaveState;

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
public class LocalJmxMonitor implements ServiceListener {
   private static Log log = LogFactory.getLog(LocalJmxMonitor.class);

   private String interfaceName;
   private int frequency = 1;
   private TimeUnit timeUnit = TimeUnit.SECONDS;

   private CpuUsageMonitor cpuMonitor;
   private MemoryUsageMonitor memoryMonitor;
   private GcMonitor gcMonitor;
   private NetworkBytesMonitor netInMonitor;
   private NetworkBytesMonitor netOutMonitor;
   private ScheduledExecutorService exec;

   private final SlaveState slaveState;

   public LocalJmxMonitor(SlaveState slaveState) {
      this.slaveState = slaveState;
   }

   public synchronized void startMonitoringLocal() {
      exec = Executors.newScheduledThreadPool(1, new ThreadFactory() {
         AtomicInteger counter = new AtomicInteger();
         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "JmxMonitor" + counter.getAndIncrement());
         }
      });
      log.info("Gathering statistics every " + frequency + " " + timeUnit.name());
      try {
         cpuMonitor = new CpuUsageMonitor(slaveState.getTimeline());
         exec.scheduleAtFixedRate(cpuMonitor, 0, frequency, timeUnit);
         memoryMonitor = new MemoryUsageMonitor(slaveState.getTimeline());
         exec.scheduleAtFixedRate(memoryMonitor, 0, frequency, timeUnit);
         gcMonitor = new GcMonitor(slaveState.getTimeline());
         exec.scheduleAtFixedRate(gcMonitor, 0, frequency, timeUnit);
         if (interfaceName != null) {
            netInMonitor = NetworkBytesMonitor.createReceiveMonitor(interfaceName, slaveState.getTimeline());
            exec.scheduleAtFixedRate(netInMonitor, 0, frequency, timeUnit);
            netOutMonitor = NetworkBytesMonitor.createTransmitMonitor(interfaceName, slaveState.getTimeline());
            exec.scheduleAtFixedRate(netOutMonitor, 0, frequency, timeUnit);
         }
      } catch (Exception e) {
         log.error(e.getMessage(), e);
      }
   }
   
   public synchronized void stopMonitoringLocal() {
      cpuMonitor.stop();
      memoryMonitor.stop();
      gcMonitor.stop();
      if (interfaceName != null) {
         netInMonitor.stop();
         netOutMonitor.stop();
      }
      exec.shutdownNow();
      try {
         exec.awaitTermination(2 * frequency, timeUnit);
      } catch (InterruptedException e) {
         log.error("Failed to terminate local monitoring.");
      }
      this.exec = null;

   }

   public void setInterfaceName(String interfaceName) {
      this.interfaceName = interfaceName;
   }

   public void setTimeUnit(TimeUnit timeUnit) {
      this.timeUnit = timeUnit;
   }

   public void setFrequency(int frequency) {
      this.frequency = frequency;
   }

   @Override
   public void beforeServiceStart() {}

   @Override
   public void afterServiceStart() {}

   @Override
   public void beforeServiceStop(boolean graceful) {}

   @Override
   public void afterServiceStop(boolean graceful) {}

   @Override
   public void serviceDestroyed() {
      stopMonitoringLocal();
      slaveState.removeServiceListener(this);
   }
}
