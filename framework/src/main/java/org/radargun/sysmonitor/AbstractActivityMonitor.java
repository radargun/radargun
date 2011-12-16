package org.radargun.sysmonitor;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Mircea Markus <mircea.markus@gmail.com>
 */
public abstract class AbstractActivityMonitor implements Runnable {

   static final ObjectName OS_NAME = getOSName();

   static final String PROCESSING_CAPACITY_ATTR = "ProcessingCapacity";
   static final String PROCESS_UP_TIME = "Uptime";
   static final ObjectName RUNTIME_NAME = getRuntimeName();
   static final NumberFormat PERCENT_FORMATTER = NumberFormat.getPercentInstance();

   private static ObjectName getRuntimeName() {
      try {
         return new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);
      } catch (MalformedObjectNameException ex) {
         throw new RuntimeException(ex);
      }
   }

   private static ObjectName getOSName() {
      try {
         return new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
      } catch (MalformedObjectNameException ex) {
         throw new RuntimeException(ex);
      }
   }

   public static long getCpuMultiplier(MBeanServerConnection con) throws Exception {
      Number num;
      try {
         num = (Number) con.getAttribute(OS_NAME, PROCESSING_CAPACITY_ATTR);
      } catch (AttributeNotFoundException e) {
         num = 1;
      }
      return num.longValue();
   }

   public static String formatPercent(double value) {
      return PERCENT_FORMATTER.format(value / 100);
   }

   protected final List<BigDecimal> measurements = new ArrayList<BigDecimal>();

   public final void addMeasurement(BigDecimal value) {
      measurements.add(value);
   }

   protected void addMeasurementAsPercentage(long v) {
      addMeasurement(new BigDecimal(v).divide(new BigDecimal(10)));
   }

   public LinkedHashMap<Integer, BigDecimal> formatForGraph(int interval) {
      int x = 0;
      LinkedHashMap<Integer, BigDecimal> map = new LinkedHashMap<Integer, BigDecimal>(measurements.size());
      for (BigDecimal y : measurements) {
         map.put(x, y);
         x += interval;
      }
      return map;
   }

   public Integer getMeasurementCount() {
      return measurements.size();
   }
}
