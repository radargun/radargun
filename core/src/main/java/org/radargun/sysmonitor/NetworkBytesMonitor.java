package org.radargun.sysmonitor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;

/**
 * Parse the /proc/net/dev file for a value on the specified network interface
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class NetworkBytesMonitor implements Monitor {
   
   private static int TRANSMIT_BYTES_INDEX = 8;
   private static int RECEIVE_BYTES_INDEX = 0;

   /** The serialVersionUID */
   private static final long serialVersionUID = -260611570251145013L;

   private static Log log = LogFactory.getLog(NetworkBytesMonitor.class);

   private final Timeline timeline;
   String iface;
   int valueIndex = -1;
   BigDecimal initialValue;

   public static NetworkBytesMonitor createReceiveMonitor(String iface, Timeline timeline) {
      return new NetworkBytesMonitor(iface, RECEIVE_BYTES_INDEX, timeline);
   }

   public static NetworkBytesMonitor createTransmitMonitor(String iface, Timeline timeline) {
      return new NetworkBytesMonitor(iface, TRANSMIT_BYTES_INDEX, timeline);
   }

   private NetworkBytesMonitor(String iface, int valueIndex, Timeline timeline) {
      this.timeline = timeline;
      this.iface = iface;
      this.valueIndex = valueIndex;
   }

   public void run() {
      FileInputStream inputStream;
      try {
         inputStream = new FileInputStream("/proc/net/dev");
         BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
         try {
            String line = br.readLine();
            while (line != null) {
               line = line.trim();
               if (line.startsWith(iface)) {
                  String[] vals = line.split(":")[1].trim().split("\\s+");
                  // Start monitoring from zero and then increase
                  if (initialValue == null) {
                     initialValue = new BigDecimal(vals[valueIndex]);
                     timeline.addValue(getCategory(), new Timeline.Value(0));
                  } else {
                     timeline.addValue(getCategory(), new Timeline.Value(new BigDecimal(vals[valueIndex]).subtract(initialValue)));
                  }
                  break;
               }
               line = br.readLine();
            }
         } catch (Exception e) {
            log.error("Exception occurred while reading /proc/net/dev.", e);
         } finally {
            try {
               if (br != null) br.close();
               if (inputStream != null) inputStream.close();
            } catch (IOException e) {
               log.error("Exception occurred while closing /proc/net/dev.", e);
            }
         }
      } catch (FileNotFoundException e) {
         log.error("File /proc/net/dev was not found!", e);
      }
   }

   private String getCategory() {
      return String.format("Network %s on %s", valueIndex == TRANSMIT_BYTES_INDEX ? "TX" : "RX", iface);
   }

   @Override
   public void start() {
      // nothing to do
   }

   @Override
   public void stop() {
      // nothing to do
   }
}
