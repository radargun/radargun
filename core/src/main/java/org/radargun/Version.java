package org.radargun;

public final class Version {
   public static final String SCHEMA_VERSION = "3.0"; // TODO: read version from plugin
   public static final String VERSION = "3.0.0-SNAPSHOT";

   private Version() {}

   public static void main(String[] args) {
      System.out.println("=== RadarGun ===");
      System.out.println("Version: " + VERSION);
   }
}
