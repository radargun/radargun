package org.radargun.sysmonitor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.MBeanServerConnection;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Galder Zamarreno
*/
public class MemoryUsageMonitor extends AbstractActivityMonitor {

   private static Log log = LogFactory.getLog(MemoryUsageMonitor.class);

   static final NumberFormat DECIMAL_FORMATTER = NumberFormat.getNumberInstance();
   final MBeanServerConnection con;
   final MemoryMXBean memMbean;
   long genUsed;
   long genCapacity;
   long genMaxCapacity;

   static {
      DECIMAL_FORMATTER.setGroupingUsed(true);
      DECIMAL_FORMATTER.setMaximumFractionDigits(2);
   }

   MemoryUsageMonitor(MBeanServerConnection con) throws IOException {
      this.con = con;
      this.memMbean = ManagementFactory.newPlatformMXBeanProxy(con,
                                                               ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);
   }

   public void run() {
      try {
         MemoryUsage mem = memMbean.getHeapMemoryUsage();
         genUsed = mem.getUsed();
         genCapacity = mem.getCommitted();
         genMaxCapacity = mem.getMax();

         addMeasurement(new BigDecimal(genUsed));

         log.trace("Memory usage: used=" + formatDecimal(genUsed) + " B, size=" + formatDecimal(genCapacity) +
                         " B, max=" + formatDecimal(genMaxCapacity));
      } catch (Exception e) {
         log.error(e);
      }
   }

   private String formatDecimal(long value) {
      return DECIMAL_FORMATTER.format(value);
   }

   public void convertToMb() {
      List<BigDecimal> mbs = new ArrayList<BigDecimal>(measurements.size());
      for (BigDecimal v : measurements) {
         mbs.add(v.divide(new BigDecimal(1024*1024)));
      }
      measurements.clear();
      measurements.addAll(mbs);
   }
}
