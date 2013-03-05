package org.radargun.sysmonitor;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanServerConnection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Galder Zamarreno
 */
public class MemoryUsageMonitor extends AbstractActivityMonitor implements Serializable {

   /** The serialVersionUID */
   private static final long serialVersionUID = 2763306122547969008L;

   private static Log log = LogFactory.getLog(MemoryUsageMonitor.class);

   boolean running = true;

   static final NumberFormat DECIMAL_FORMATTER = NumberFormat.getNumberInstance();
   long genUsed;
   long genCapacity;
   long genMaxCapacity;

   static {
      DECIMAL_FORMATTER.setGroupingUsed(true);
      DECIMAL_FORMATTER.setMaximumFractionDigits(2);
   }

   public void stop() {
      running = false;
   }

   public void run() {
      if (running) {
         if (this.firstMeasurementTime == -1) {
            this.firstMeasurementTime = System.currentTimeMillis();
         }
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

            addMeasurement(new BigDecimal(genUsed));

            log.trace("Memory usage: used=" + formatDecimal(genUsed) + " B, size=" + formatDecimal(genCapacity)
                  + " B, max=" + formatDecimal(genMaxCapacity));
         } catch (Exception e) {
            log.error(e);
         }
      }
   }

   private String formatDecimal(long value) {
      return DECIMAL_FORMATTER.format(value);
   }

   public void convertToMb() {
      List<BigDecimal> mbs = new ArrayList<BigDecimal>(measurements.size());
      for (BigDecimal v : measurements) {
         mbs.add(v.divide(new BigDecimal(1024 * 1024)));
      }
      measurements.clear();
      measurements.addAll(mbs);
   }
}
