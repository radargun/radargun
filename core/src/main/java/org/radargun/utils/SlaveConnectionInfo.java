package org.radargun.utils;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds the list of network interfaces and their IP addresses for a single slave.
 */
public class SlaveConnectionInfo implements Serializable {
   private int slaveIndex;
   private Map<String, ArrayList<InetAddress>> interfaceToAddrs = new HashMap<>();

   public void addAddresses(int slaveIndex, String interfaceName, ArrayList<InetAddress> addresses) {
      this.slaveIndex = slaveIndex;
      interfaceToAddrs.put(interfaceName, addresses);
   }

   public List<InetAddress> getAddresses(String interfaceName) {
      return interfaceToAddrs.get(interfaceName);
   }

   public String getAddressesAsString(String interfaceName, String delimiter) {
      return String.join(delimiter, interfaceToAddrs.get(interfaceName).stream().map(addr -> addr.getHostAddress()).collect(Collectors.toList()));
   }

   public Set<String> getInterfaceNames() {
      return interfaceToAddrs.keySet();
   }

   public int getSlaveIndex() {
      return slaveIndex;
   }

   /**
    * Used as a message ID for master-slave communication.
    */
   public static class Request implements Serializable {
   }
}
