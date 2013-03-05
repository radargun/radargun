package org.radargun.sysmonitor;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

import javax.management.MBeanServerConnection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Galder Zamarreno
 */
public class CpuUsageMonitor extends AbstractActivityMonitor implements Serializable {

   /** The serialVersionUID */
   private static final long serialVersionUID = 6632071089421842090L;

   private static Log log = LogFactory.getLog(CpuUsageMonitor.class);
   static final String PROCESS_CPU_TIME_ATTR = "ProcessCpuTime";

   boolean running = true;

   long cpuTime;
   long prevCpuTime;
   long upTime;
   long prevUpTime;

   static {
      PERCENT_FORMATTER.setMinimumFractionDigits(1);
      PERCENT_FORMATTER.setMaximumIntegerDigits(3);
   }

   public void stop() {
      running = false;
   }

   public void run() {
      if (running) {
         if (this.firstMeasurementTime == -1) {
            this.firstMeasurementTime = System.currentTimeMillis();
         }
         try {
            prevCpuTime = cpuTime;
            prevUpTime = upTime;

            MBeanServerConnection con = ManagementFactory.getPlatformMBeanServer();
            if (con == null)
               throw new IllegalStateException("PlatformMBeanServer not started!");

            long cpuTimeMultiplier = getCpuMultiplier(con);
            OperatingSystemMXBean os = ManagementFactory.newPlatformMXBeanProxy(con,
                  ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, OperatingSystemMXBean.class);
            int procCount = os.getAvailableProcessors();

            Long jmxCpuTime = (Long) con.getAttribute(OS_NAME, PROCESS_CPU_TIME_ATTR);
            cpuTime = jmxCpuTime * cpuTimeMultiplier;
            Long jmxUpTime = (Long) con.getAttribute(RUNTIME_NAME, PROCESS_UP_TIME);
            upTime = jmxUpTime;
            long upTimeDiff = (upTime * 1000000) - (prevUpTime * 1000000);

            long procTimeDiff = (cpuTime / procCount) - (prevCpuTime / procCount);

            long cpuUsage = upTimeDiff > 0 ? Math.min((long) (1000 * (float) procTimeDiff / (float) upTimeDiff), 1000)
                  : 0;

            addMeasurementAsPercentage(cpuUsage);

            log.trace("Cpu usage: " + formatPercent(cpuUsage * 0.1d));
         } catch (Exception e) {
            log.error("Exception!", e);
         }
      }
   }
}
