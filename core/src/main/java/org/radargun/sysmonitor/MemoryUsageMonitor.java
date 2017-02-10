package org.radargun.sysmonitor;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.text.NumberFormat;

import org.radargun.reporting.Timeline;
import org.radargun.traits.JmxConnectionProvider;

/**
 * In each invocation of the {@link #run()} method, retrieves information
 * about memory size and usage from JMX and reports it into the {@link Timeline}.
 *
 * @author Galder Zamarreno
 */
public class MemoryUsageMonitor extends JmxMonitor {
   private static final String MEMORY_USAGE = "Memory usage";
   private static final NumberFormat DECIMAL_FORMATTER = NumberFormat.getNumberInstance();

   static {
      DECIMAL_FORMATTER.setGroupingUsed(true);
      DECIMAL_FORMATTER.setMaximumFractionDigits(2);
   }

   public MemoryUsageMonitor(JmxConnectionProvider jmxConnectionProvider, Timeline timeline) {
      super(jmxConnectionProvider, timeline);
   }

   public synchronized void run() {
      try {
         if (connection == null) {
            log.warn("MBean connection is not open, cannot read memory stats");
            return;
         }

         MemoryMXBean memMbean = ManagementFactory.newPlatformMXBeanProxy(connection, ManagementFactory.MEMORY_MXBEAN_NAME,
            MemoryMXBean.class);
         MemoryUsage mem = memMbean.getHeapMemoryUsage();

         timeline.addValue(Timeline.Category.sysCategory(MEMORY_USAGE), new Timeline.Value(mem.getUsed() / 1048576));

         log.trace("Memory usage: used=" + formatDecimal(mem.getUsed()) + " B, size=" + formatDecimal(mem.getCommitted())
            + " B, max=" + formatDecimal(mem.getMax()));
      } catch (Exception e) {
         log.error("Error in JMX memory stats retrieval", e);
      }
   }

   private String formatDecimal(long value) {
      return DECIMAL_FORMATTER.format(value);
   }

   @Override
   public synchronized void start() {
      super.start();
      timeline.addValue(Timeline.Category.sysCategory(MEMORY_USAGE), new Timeline.Value(0));
   }

   @Override
   public synchronized void stop() {
      super.stop();
      timeline.addValue(Timeline.Category.sysCategory(MEMORY_USAGE), new Timeline.Value(0));
   }
}
