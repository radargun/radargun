package org.radargun.sysmonitor;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

import javax.management.MBeanServerConnection;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;

/**
 * @author Galder Zamarreno
 */
public class CpuUsageMonitor extends AbstractActivityMonitor implements Serializable {

   /** The serialVersionUID */
   private static final long serialVersionUID = 6632071089421842090L;
   protected static final String CPU_USAGE = "CPU usage";

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

   public CpuUsageMonitor(Timeline timeline) {
      super(timeline);
   }

   public void stop() {
      running = false;
   }

   public void run() {
      if (running) {
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

            // TODO: remove that decimal !@#%$
            timeline.addValue(CPU_USAGE, new Timeline.Value(cpuUsage * 0.1d));
         } catch (Exception e) {
            log.error("Exception!", e);
         }
      }
   }
}
