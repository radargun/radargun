package org.radargun.sysmonitor;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.text.NumberFormat;
import javax.management.MBeanServerConnection;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;

/**
 * @author Galder Zamarreno
 */
public class MemoryUsageMonitor extends AbstractActivityMonitor implements Serializable {

   /** The serialVersionUID */
   private static final long serialVersionUID = 2763306122547969008L;

   private static Log log = LogFactory.getLog(MemoryUsageMonitor.class);
   private static final String MEMORY_USAGE = "Memory usage";

   boolean running = true;

   static final NumberFormat DECIMAL_FORMATTER = NumberFormat.getNumberInstance();
   long genUsed;
   long genCapacity;
   long genMaxCapacity;

   static {
      DECIMAL_FORMATTER.setGroupingUsed(true);
      DECIMAL_FORMATTER.setMaximumFractionDigits(2);
   }

   public MemoryUsageMonitor(Timeline timeline, int slaveIndex) {
      super(timeline);
   }

   public void stop() {
      running = false;
   }

   public void run() {
      if (running) {
         try {
            MBeanServerConnection con = ManagementFactory.getPlatformMBeanServer();
            if (con == null)
               throw new IllegalStateException("PlatformMBeanServer not started!");

            MemoryMXBean memMbean = ManagementFactory.newPlatformMXBeanProxy(con, ManagementFactory.MEMORY_MXBEAN_NAME,
                  MemoryMXBean.class);
            MemoryUsage mem = memMbean.getHeapMemoryUsage();
            genUsed = mem.getUsed();
            genCapacity = mem.getCommitted();
            genMaxCapacity = mem.getMax();

            //addMeasurement(new BigDecimal(genUsed));
            timeline.addValue(MEMORY_USAGE, new Timeline.Value(System.currentTimeMillis(), genUsed / 1048576));

            log.trace("Memory usage: used=" + formatDecimal(genUsed) + " B, size=" + formatDecimal(genCapacity)
                  + " B, max=" + formatDecimal(genMaxCapacity));
         } catch (Exception e) {
            log.error("Error in JMX memory stats retrieval", e);
         }
      }
   }

   private String formatDecimal(long value) {
      return DECIMAL_FORMATTER.format(value);
   }
}
