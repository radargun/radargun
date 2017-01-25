package org.radargun.sysmonitor;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

import org.radargun.reporting.Timeline;
import org.radargun.traits.JmxConnectionProvider;

/**
 * UNIX-only: checks number of open descriptors in given process
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class OpenFilesMonitor extends JmxMonitor {
   private static final String OPEN_FILES = "Open files";
   private static final String OPEN_FILE_DESCRIPTOR_COUNT = "OpenFileDescriptorCount";
   private static final String MAX_FILE_DESCRIPTOR_COUNT = "MaxFileDescriptorCount";

   public OpenFilesMonitor(JmxConnectionProvider jmxConnectionProvider, Timeline timeline) {
      super(jmxConnectionProvider, timeline);
   }

   public synchronized void run() {
      try {
         if (connection == null) {
            log.warn("MBean connection is not open, cannot read open files stats");
            return;
         }

         OperatingSystemMXBean osBean = ManagementFactory.newPlatformMXBeanProxy(connection, ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
            OperatingSystemMXBean.class);
         Long openFiles = (Long) connection.getAttribute(osBean.getObjectName(), OPEN_FILE_DESCRIPTOR_COUNT);
         Long maxOpenFiles = (Long) connection.getAttribute(osBean.getObjectName(), MAX_FILE_DESCRIPTOR_COUNT);
         if (openFiles != null) {
            timeline.addValue(Timeline.Category.sysCategory(OPEN_FILES), new Timeline.Value(openFiles));
         }
         log.tracef("Open files: open=%s, max=%s", openFiles, maxOpenFiles);
      } catch (Exception e) {
         log.error("Error in open files stats retrieval", e);
      }
   }

   @Override
   public synchronized void start() {
      super.start();
      timeline.addValue(Timeline.Category.sysCategory(OPEN_FILES), new Timeline.Value(0));
   }

   @Override
   public synchronized void stop() {
      super.stop();
      timeline.addValue(Timeline.Category.sysCategory(OPEN_FILES), new Timeline.Value(0));
   }
}

