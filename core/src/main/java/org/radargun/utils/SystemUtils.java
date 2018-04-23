package org.radargun.utils;

/**
 * Helper for OS
 */
public class SystemUtils {

   public static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().startsWith("linux");
   public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

   private SystemUtils() {
   }
}
