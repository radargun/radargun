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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Parse the /proc/net/dev file for a value on the specified network interface
 * 
 * @author Alan Field
 */
public class NetworkBytesMonitor extends AbstractActivityMonitor implements Serializable {
   
   public static int TRANSMIT_BYTES_INDEX = 8;
   public static int RECEIVE_BYTES_INDEX = 0;

   /** The serialVersionUID */
   private static final long serialVersionUID = -260611570251145013L;

   private static Log log = LogFactory.getLog(NetworkBytesMonitor.class);

   boolean running = true;
   String iface;
   int valueIndex = -1;
   BigDecimal initialValue;

   public static NetworkBytesMonitor NetworkBytesMonitorFactory(String iface, int valueIndex) {
      return new NetworkBytesMonitor(iface, valueIndex);
   }
   
   private NetworkBytesMonitor(String iface, int valueIndex) {
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
               String line = br.readLine().trim();
               while (line != null) {
                  if (line.startsWith(iface)) {
                     String[] vals = line.split(":")[1].trim().split("\\s+");
                     // Start monitoring from zero and then increase
                     if (initialValue == null) {
                        initialValue = new BigDecimal(vals[valueIndex]);
                        this.addMeasurement(new BigDecimal(0));
                     } else {
                        this.addMeasurement(new BigDecimal(vals[valueIndex]).subtract(initialValue));
                     }
                     break;
                  }
                  line = br.readLine().trim();
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

}
