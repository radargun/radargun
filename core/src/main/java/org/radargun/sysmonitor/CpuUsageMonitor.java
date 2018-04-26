package org.radargun.sysmonitor;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.TimeUnit;

import org.radargun.reporting.Timeline;
import org.radargun.traits.JmxConnectionProvider;

/**
 * @author Galder Zamarreno
 */
public class CpuUsageMonitor extends JmxMonitor {
   private static final String CPU_USAGE = "CPU usage";
   private static final String PROCESS_CPU_TIME_ATTR = "ProcessCpuTime";

   long prevCpuTime;
   long prevUpTime;

   static {
      PERCENT_FORMATTER.setMinimumFractionDigits(1);
      PERCENT_FORMATTER.setMaximumIntegerDigits(3);
   }

   public CpuUsageMonitor(JmxConnectionProvider jmxConnectionProvider, Timeline timeline) {
      super(jmxConnectionProvider, timeline);
   }

   public synchronized void runMonitor() {
      try {
         if (connection == null) {
            log.warn("MBean connection is not open, cannot read CPU stats");
            return;
         }

         long cpuTimeMultiplier = getCpuMultiplier(connection);
         OperatingSystemMXBean os = ManagementFactory.newPlatformMXBeanProxy(connection,
               ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, OperatingSystemMXBean.class);
         int procCount = os.getAvailableProcessors();

         long jmxCpuTime = (Long) connection.getAttribute(OS_NAME, PROCESS_CPU_TIME_ATTR);
         long cpuTime = jmxCpuTime * cpuTimeMultiplier;
         long upTime = (Long) connection.getAttribute(RUNTIME_NAME, PROCESS_UP_TIME);

         long upTimeDiff = TimeUnit.MILLISECONDS.toNanos(upTime - prevUpTime);
         long procTimeDiff = (cpuTime - prevCpuTime) / procCount; // already in nanoseconds
         double cpuUsage = Math.min(1d, Math.max(0d, (double) procTimeDiff / (double) upTimeDiff));

         timeline.addValue(Timeline.Category.sysCategory(CPU_USAGE), new Timeline.Value(cpuUsage));
         log.tracef("Current CPU usage: %.1f%%", 100 * cpuUsage);
         prevCpuTime = cpuTime;
         prevUpTime = upTime;
      } catch (Exception e) {
         log.error("Exception!", e);
      }
   }

   @Override
   public synchronized void start() {
      super.start();
      timeline.addValue(Timeline.Category.sysCategory(CPU_USAGE), new Timeline.Value(0));
   }

   @Override
   public synchronized void stop() {
      super.stop();
      timeline.addValue(Timeline.Category.sysCategory(CPU_USAGE), new Timeline.Value(0));
   }
}
