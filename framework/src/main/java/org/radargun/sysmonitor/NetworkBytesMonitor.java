package org.radargun.sysmonitor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.math.BigDecimal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Alan Field
 */
public class NetworkBytesMonitor extends AbstractActivityMonitor implements Serializable {

   /** The serialVersionUID */
   private static final long serialVersionUID = -260611570251145013L;

   private static Log log = LogFactory.getLog(NetworkBytesMonitor.class);

   boolean running = true;
   String iface;
   int valueIndex = -1;

   public NetworkBytesMonitor(String iface, int valueIndex) {
      super();
      this.iface = iface;
      this.valueIndex = valueIndex;
   }

   public void stop() {
      running = false;
   }

   public void run() {
      if (running) {
         FileInputStream inputStream;
         try {
            inputStream = new FileInputStream("/proc/net/dev");
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            try {
               String line = br.readLine();
               while (line != null) {
                  String tline = line.trim();
                  if (tline.startsWith(iface)) {
                     String[] vals = tline.split(":")[1].trim().split("\\s+");
                     this.addMeasurement(new BigDecimal(vals[valueIndex]));
                     break;
                  }
                  line = br.readLine();
               }
               br.close();
            } catch(Exception e) {
               log.error("Exception occurred while reading /proc/net/dev.", e);
            } finally {
               if (inputStream != null) {
                  try {
                     inputStream.close();
                  } catch (IOException e) {
                     log.error("Exception occurred while closing /proc/net/dev.", e);
                  }
               }
            }
         } catch (FileNotFoundException e) {
            log.error("File /proc/net/dev was not found!", e);
         }
      }
   }

}
