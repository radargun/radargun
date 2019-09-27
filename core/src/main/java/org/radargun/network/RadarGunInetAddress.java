package org.radargun.network;

public class RadarGunInetAddress {

   private final String host;
   private final int port;

   public RadarGunInetAddress(String host, int port) {
      this.host = host;
      this.port = port;
   }

   public String getHost() {
      return host;
   }

   public int getPort() {
      return port;
   }
}
