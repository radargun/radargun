package org.radargun.sysmonitor;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.text.NumberFormat;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.traits.JmxConnectionProvider;

/**
 * Provides constants for JMX access.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public abstract class JmxMonitor implements Monitor {
   protected final Log log = LogFactory.getLog(getClass());

   static final ObjectName OS_NAME = getOSName();
   static final String PROCESSING_CAPACITY_ATTR = "ProcessingCapacity";
   static final String PROCESS_UP_TIME = "Uptime";
   static final ObjectName RUNTIME_NAME = getRuntimeName();
   static final NumberFormat PERCENT_FORMATTER = NumberFormat.getPercentInstance();

   protected final Timeline timeline;
   protected final JmxConnectionProvider jmxConnectionProvider;
   protected MBeanServerConnection connection;
   protected JMXConnector connector;

   public JmxMonitor(JmxConnectionProvider jmxConnectionProvider, Timeline timeline) {
      this.jmxConnectionProvider = jmxConnectionProvider;
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

   @Override
   public synchronized void start() {
      if (jmxConnectionProvider == null) {
         connection = ManagementFactory.getPlatformMBeanServer();
      } else {
         connector = jmxConnectionProvider.getConnector();
         if (connector != null) {
            try {
               connection = connector.getMBeanServerConnection();
            } catch (IOException e) {
               log.error("Failed to connect to MBean server", e);
            }
         }
      }
   }

   @Override
   public synchronized void stop() {
      if (connector != null) {
         try {
            connector.close();
         } catch (IOException e) {
            log.error("Failed to close JMX connection", e);
         }
         connector = null;
      }
   }

   @Override
   public boolean equals(Object o) {
      return o != null && o.getClass() == this.getClass();
   }
}
