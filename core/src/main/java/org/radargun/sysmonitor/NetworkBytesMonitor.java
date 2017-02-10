package org.radargun.sysmonitor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;

/**
 * Parse the /proc/net/dev file for a value on the specified network interface
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class NetworkBytesMonitor implements Monitor {

   private static final int TRANSMIT_BYTES_INDEX = 8;
   private static final int RECEIVE_BYTES_INDEX = 0;

   /** The serialVersionUID */
   private static final long serialVersionUID = -260611570251145013L;

   private static Log log = LogFactory.getLog(NetworkBytesMonitor.class);

   private final Timeline timeline;
   private String iface;
   private int valueIndex = -1;
   private long previousValue;
   private long previousTime;

   private NetworkBytesMonitor(String iface, int valueIndex, Timeline timeline) {
      this.timeline = timeline;
      this.iface = iface;
      this.valueIndex = valueIndex;
   }

   public static NetworkBytesMonitor createReceiveMonitor(String iface, Timeline timeline) {
      return new NetworkBytesMonitor(iface, RECEIVE_BYTES_INDEX, timeline);
   }

   public static NetworkBytesMonitor createTransmitMonitor(String iface, Timeline timeline) {
      return new NetworkBytesMonitor(iface, TRANSMIT_BYTES_INDEX, timeline);
   }

   public void run() {
      try (FileInputStream inputStream = new FileInputStream("/proc/net/dev"); BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
         try {
            String line = br.readLine();
            while (line != null) {
               line = line.trim();
               if (line.startsWith(iface)) {
                  long now = System.currentTimeMillis();
                  String[] vals = line.split(":")[1].trim().split("\\s+");
                  if (previousValue == 0) {
                     timeline.addValue(getCategory(), new Timeline.Value(0));
                  } else {
                     long bytesSinceLastTime = Long.valueOf(vals[valueIndex]) - previousValue;
                     long trafficPerSecond = bytesSinceLastTime * TimeUnit.SECONDS.toMillis(1) / (now - previousTime);
                     timeline.addValue(getCategory(), new Timeline.Value(trafficPerSecond));
                  }
                  previousValue = Long.valueOf(vals[valueIndex]);
                  previousTime = now;
                  break;
               }
               line = br.readLine();
            }
         } catch (Exception e) {
            log.error("Exception occurred while reading /proc/net/dev.", e);
         }
      } catch (FileNotFoundException e) {
         log.error("File /proc/net/dev was not found!", e);
      } catch (IOException e) {
         log.error("Error while getting input stream", e);
      }
   }

   private Timeline.Category getCategory() {
      return Timeline.Category.sysCategory(String.format("Network %s on %s [bytes per second]", valueIndex == TRANSMIT_BYTES_INDEX ? "TX" : "RX", iface));
   }

   @Override
   public void start() {
      // nothing to do
   }

   @Override
   public void stop() {
      // nothing to do
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      NetworkBytesMonitor that = (NetworkBytesMonitor) o;

      if (valueIndex != that.valueIndex) return false;
      if (!iface.equals(that.iface)) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = iface.hashCode();
      result = 31 * result + valueIndex;
      return result;
   }
}
