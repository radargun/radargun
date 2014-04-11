package org.radargun.sysmonitor;

import static java.lang.management.ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE;
import static java.lang.management.ManagementFactory.newPlatformMXBeanProxy;

import java.io.Serializable;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;

/**
 * In each invocation of the {@link #run()} method, retrieves information
 * about garbage collection from JMX and reports it into the {@link Timeline}.
 *
 * @author Galder Zamarreno
 */
public class GcMonitor extends AbstractActivityMonitor implements Serializable {

   /** The serialVersionUID */
   private static final long serialVersionUID = 8983759071129628827L;

   private static Log log = LogFactory.getLog(GcMonitor.class);
   private static final String GC_USAGE = "GC CPU usage";

   boolean running = true;

   long gcTime;
   long prevGcTime;
   long upTime;
   long prevUpTime;

   public GcMonitor(Timeline timeline) {
      super(timeline);
   }

   public void stop() {
      running = false;
   }

   public void run() {
      if (running) {
         try {
            prevUpTime = upTime;
            prevGcTime = gcTime;
            MBeanServerConnection con = ManagementFactory.getPlatformMBeanServer();
            if (con == null)
               throw new IllegalStateException("PlatformMBeanServer not started!");

            OperatingSystemMXBean os = ManagementFactory.newPlatformMXBeanProxy(con,
                  ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, OperatingSystemMXBean.class);
            int procCount = os.getAvailableProcessors();

            List<GarbageCollectorMXBean> gcMbeans = getGarbageCollectorMXBeans(con);
            gcTime = -1;
            for (GarbageCollectorMXBean gcBean : gcMbeans)
               gcTime += gcBean.getCollectionTime();

            long processGcTime = gcTime * 1000000 / procCount;
            long prevProcessGcTime = prevGcTime * 1000000 / procCount;
            long processGcTimeDiff = processGcTime - prevProcessGcTime;

            Long jmxUpTime = (Long) con.getAttribute(RUNTIME_NAME, PROCESS_UP_TIME);
            upTime = jmxUpTime;
            long upTimeDiff = (upTime * 1000000) - (prevUpTime * 1000000);

            long gcUsage = upTimeDiff > 0 ? Math.min((long) (1000 * (float) processGcTimeDiff / (float) upTimeDiff),
                  1000) : 0;

            // TODO: remove that decimal !@#%$
            timeline.addValue(GC_USAGE, new Timeline.Value(gcUsage * 0.1d));

         } catch (Exception e) {
            log.error(e.getMessage(), e);
         }
      }
   }

   private List<GarbageCollectorMXBean> getGarbageCollectorMXBeans(MBeanServerConnection con) throws Exception {
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
