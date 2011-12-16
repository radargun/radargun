package org.radargun.sysmonitor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.lang.management.ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE;
import static java.lang.management.ManagementFactory.newPlatformMXBeanProxy;

/**
 * @author Galder Zamarreno
*/
public class GcMonitor extends AbstractActivityMonitor {

   private static Log log = LogFactory.getLog(GcMonitor.class);

   final MBeanServerConnection con;
   final int procCount;
   final long cpuTimeMultiplier;
   List<GarbageCollectorMXBean> gcMbeans;
   long gcTime;
   long prevGcTime;
   long upTime;
   long prevUpTime;

   GcMonitor(MBeanServerConnection con) throws Exception {
      this.con = con;
      OperatingSystemMXBean os = ManagementFactory.newPlatformMXBeanProxy(con,
                                                                          ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, OperatingSystemMXBean.class);
      procCount = os.getAvailableProcessors();
      cpuTimeMultiplier = getCpuMultiplier(con);
   }

   public void run() {
      try {
         prevUpTime = upTime;
         prevGcTime = gcTime;

         gcMbeans = getGarbageCollectorMXBeans();
         gcTime = -1;
         for (GarbageCollectorMXBean gcBean : gcMbeans)
            gcTime += gcBean.getCollectionTime();

         long processGcTime = gcTime * 1000000 / procCount;
         long prevProcessGcTime = prevGcTime * 1000000 / procCount;
         long processGcTimeDiff = processGcTime - prevProcessGcTime;

         Long jmxUpTime = (Long) con.getAttribute(RUNTIME_NAME, PROCESS_UP_TIME);
         upTime = jmxUpTime;
         long upTimeDiff = (upTime * 1000000) - (prevUpTime * 1000000);

         long gcUsage = upTimeDiff > 0 ? Math.min((long)
                                                        (1000 * (float) processGcTimeDiff / (float) upTimeDiff), 1000) : 0;

         addMeasurementAsPercentage(gcUsage);


         log.trace("GC activity: " + formatPercent(gcUsage * 0.1d));
      } catch (Exception e) {
         log.error(e.getMessage(), e);
      }
   }

   private List<GarbageCollectorMXBean> getGarbageCollectorMXBeans() throws Exception {
      List<GarbageCollectorMXBean> gcMbeans = null;
      // TODO: List changes, so can't really cache apparently, but how performant is this?
      if (con != null) {
         ObjectName gcName = new ObjectName(GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*");
         Set<ObjectName> mbeans = con.queryNames(gcName, null);
         if (mbeans != null) {
            gcMbeans = new ArrayList<GarbageCollectorMXBean>();
            for (ObjectName on : mbeans) {
               String name = GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",name=" + on.getKeyProperty("name");
               GarbageCollectorMXBean mbean = newPlatformMXBeanProxy(con, name, GarbageCollectorMXBean.class);
               gcMbeans.add(mbean);
            }
         }
      }
      return gcMbeans;
   }
}
