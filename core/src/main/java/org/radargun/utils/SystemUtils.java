package org.radargun.utils;

/**
 * Helper for OS
 */
public class SystemUtils {

   public static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().startsWith("linux");

   private SystemUtils() {
   }
}
