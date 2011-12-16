package org.radargun.sysmonitor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.MBeanServerConnection;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * @author Galder Zamarreno
*/
public class CpuUsageMonitor extends AbstractActivityMonitor {

   private static Log log = LogFactory.getLog(CpuUsageMonitor.class);
   static final String PROCESS_CPU_TIME_ATTR = "ProcessCpuTime";

   final MBeanServerConnection con;
   final long cpuTimeMultiplier;
   final int procCount;
   long cpuTime;
   long prevCpuTime;
   long upTime;
   long prevUpTime;

   static {
      PERCENT_FORMATTER.setMinimumFractionDigits(1);
      PERCENT_FORMATTER.setMaximumIntegerDigits(3);
   }

   CpuUsageMonitor(MBeanServerConnection con) throws Exception {
      this.con = con;
      this.cpuTimeMultiplier = getCpuMultiplier(con);
      OperatingSystemMXBean os = ManagementFactory.newPlatformMXBeanProxy(con,
                                                                          ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, OperatingSystemMXBean.class);
      procCount = os.getAvailableProcessors();
   }

   public void run() {
      try {
         prevCpuTime = cpuTime;
         prevUpTime = upTime;

         Long jmxCpuTime = (Long) con.getAttribute(OS_NAME, PROCESS_CPU_TIME_ATTR);
         cpuTime = jmxCpuTime * cpuTimeMultiplier;
         Long jmxUpTime = (Long) con.getAttribute(RUNTIME_NAME, PROCESS_UP_TIME);
         upTime = jmxUpTime;
         long upTimeDiff = (upTime * 1000000) - (prevUpTime * 1000000);

         long procTimeDiff = (cpuTime / procCount) - (prevCpuTime / procCount);

         long cpuUsage = upTimeDiff > 0 ? Math.min((long)
                                                         (1000 * (float) procTimeDiff / (float) upTimeDiff), 1000) : 0;


         addMeasurementAsPercentage(cpuUsage);

         log.trace("Cpu usage: " + formatPercent(cpuUsage * 0.1d));
      } catch (Exception e) {
         log.error("Exception!", e);
      }
   }
}
