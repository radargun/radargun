/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.sysmonitor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.math.BigDecimal;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;

/**
 * Parse the /proc/net/dev file for a value on the specified network interface
 * 
 * @author Alan Field
 */
public class NetworkBytesMonitor extends AbstractActivityMonitor implements Serializable {
   
   private static int TRANSMIT_BYTES_INDEX = 8;
   private static int RECEIVE_BYTES_INDEX = 0;

   /** The serialVersionUID */
   private static final long serialVersionUID = -260611570251145013L;

   private static Log log = LogFactory.getLog(NetworkBytesMonitor.class);

   boolean running = true;
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
      super(timeline);
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
               br.close();
            } catch (Exception e) {
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

   private String getCategory() {
      return String.format("Network %s on %s", valueIndex == TRANSMIT_BYTES_INDEX ? "TX" : "RX", iface);
   }

}
