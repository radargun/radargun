package org.radargun.sysmonitor;

/**
 * @author Alan Field
 */
public class NetworkBytesOutMonitor extends NetworkBytesMonitor {

   public NetworkBytesOutMonitor(String iface) {
      super(iface, 8);
      this.iface = iface;
   }
}
