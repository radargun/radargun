package org.radargun.sysmonitor;

/**
 * @author Alan Field
 */
public class NetworkBytesInMonitor extends NetworkBytesMonitor {

   public NetworkBytesInMonitor(String iface) {
      super(iface, 0);
      this.iface = iface;
   }

}
