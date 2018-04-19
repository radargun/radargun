package org.radargun.sysmonitor;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

import org.radargun.reporting.Timeline;
import org.radargun.traits.JmxConnectionProvider;

/**
 * In each invocation of the {@link #run()} method, retrieves information
 * about loaded class count, total loaded class count and unloaded class count from JMX and
 * reports it into the {@link Timeline}.
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 *
 */
public class ClassLoadingCountMonitor extends JmxMonitor {

   private static final String LOADED_CLASS_COUNT = "Loaded Class Count";
   private static final String TOTAL_LOADED_CLASS_COUNT = "Total Loaded Class Count";
   private static final String UNLOADED_CLASS_COUNT = "Unloaded Class Count";

   public ClassLoadingCountMonitor(JmxConnectionProvider jmxConnectionProvider, Timeline timeline) {
      super(jmxConnectionProvider, timeline);
   }

   public synchronized void run() {
      try {
         if (connection == null) {
            log.warn("MBean connection is not open, cannot read memory stats");
            return;
         }

         ClassLoadingMXBean classLoadingMbean =
            ManagementFactory.newPlatformMXBeanProxy(connection, ManagementFactory.CLASS_LOADING_MXBEAN_NAME, ClassLoadingMXBean.class);
         long loadedClassCount = classLoadingMbean.getLoadedClassCount();
         long totalLoadedClassCount = classLoadingMbean.getTotalLoadedClassCount();
         long unloadedClassCount = classLoadingMbean.getUnloadedClassCount();

         timeline.addValue(Timeline.Category.sysCategory(LOADED_CLASS_COUNT), new Timeline.Value(loadedClassCount));
         timeline.addValue(Timeline.Category.sysCategory(TOTAL_LOADED_CLASS_COUNT), new Timeline.Value(totalLoadedClassCount));
         timeline.addValue(Timeline.Category.sysCategory(UNLOADED_CLASS_COUNT), new Timeline.Value(unloadedClassCount));

      } catch (Exception e) {
         log.error("Error in JMX class loading stats retrieval", e);
      }
   }

   @Override
   public synchronized void start() {
      super.start();
      timeline.addValue(Timeline.Category.sysCategory(LOADED_CLASS_COUNT), new Timeline.Value(0));
      timeline.addValue(Timeline.Category.sysCategory(TOTAL_LOADED_CLASS_COUNT), new Timeline.Value(0));
      timeline.addValue(Timeline.Category.sysCategory(UNLOADED_CLASS_COUNT), new Timeline.Value(0));
   }

   @Override
   public synchronized void stop() {
      super.stop();
      timeline.addValue(Timeline.Category.sysCategory(LOADED_CLASS_COUNT), new Timeline.Value(0));
      timeline.addValue(Timeline.Category.sysCategory(TOTAL_LOADED_CLASS_COUNT), new Timeline.Value(0));
      timeline.addValue(Timeline.Category.sysCategory(UNLOADED_CLASS_COUNT), new Timeline.Value(0));
   }
}
