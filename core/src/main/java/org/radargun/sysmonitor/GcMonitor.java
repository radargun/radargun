package org.radargun.sysmonitor;

import java.io.Serializable;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.radargun.reporting.Timeline;
import org.radargun.traits.JmxConnectionProvider;

import static java.lang.management.ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE;
import static java.lang.management.ManagementFactory.newPlatformMXBeanProxy;

/**
 * In each invocation of the {@link #run()} method, retrieves information
 * about garbage collection from JMX and reports it into the {@link Timeline}.
 *
 * @author Galder Zamarreno
 */
public class GcMonitor extends JmxMonitor implements Serializable {
   private static final String GC_USAGE = "GC CPU usage";

   private long prevGcTime;
   private long prevUpTime;

   public GcMonitor(JmxConnectionProvider jmxConnectionProvider, Timeline timeline) {
      super(jmxConnectionProvider, timeline);
   }

   public synchronized void run() {
      try {
         if (connection == null) {
            log.warn("MBean connection is not open, cannot read GC stats");
            return;
         }

         OperatingSystemMXBean os = ManagementFactory.newPlatformMXBeanProxy(connection,
            ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, OperatingSystemMXBean.class);
         int procCount = os.getAvailableProcessors();

         List<GarbageCollectorMXBean> gcMbeans = getGarbageCollectorMXBeans(connection);
         long gcTime = 0;
         for (GarbageCollectorMXBean gcBean : gcMbeans)
            gcTime += gcBean.getCollectionTime();

         long processGcTimeDiff = TimeUnit.MILLISECONDS.toNanos(gcTime - prevGcTime) / procCount;
         long upTime = (Long) connection.getAttribute(RUNTIME_NAME, PROCESS_UP_TIME);
         long upTimeDiff = TimeUnit.MILLISECONDS.toNanos(upTime - prevUpTime);

         double gcUsage = Math.min(1d, Math.max(0, (double) processGcTimeDiff / (double) upTimeDiff));

         timeline.addValue(Timeline.Category.sysCategory(GC_USAGE), new Timeline.Value(gcUsage));
         log.tracef("Current GC CPU usage: %.2f%%", 100 * gcUsage);
         prevUpTime = upTime;
         prevGcTime = gcTime;
      } catch (Exception e) {
         log.error(e.getMessage(), e);
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

   @Override
   public synchronized void start() {
      super.start();
      timeline.addValue(Timeline.Category.sysCategory(GC_USAGE), new Timeline.Value(0));
   }

   @Override
   public synchronized void stop() {
      super.stop();
      timeline.addValue(Timeline.Category.sysCategory(GC_USAGE), new Timeline.Value(0));
   }
}
