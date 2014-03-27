package org.radargun.sysmonitor;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.text.NumberFormat;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.radargun.reporting.Timeline;

/**
 * @author Mircea Markus <mircea.markus@gmail.com>
 */
public abstract class AbstractActivityMonitor implements Runnable, Serializable {

   static final ObjectName OS_NAME = getOSName();

   static final String PROCESSING_CAPACITY_ATTR = "ProcessingCapacity";
   static final String PROCESS_UP_TIME = "Uptime";
   static final ObjectName RUNTIME_NAME = getRuntimeName();
   static final NumberFormat PERCENT_FORMATTER = NumberFormat.getPercentInstance();

   protected final Timeline timeline;

   public AbstractActivityMonitor(Timeline timeline) {
      this.timeline = timeline;
   }

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
}
