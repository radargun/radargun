package org.cachebench.utils;

/**
 *
 * @author Mircea.Markus@jboss.com
 */
public class Utils {

   public static String getDurationString(long duration) {
      long secs = duration / 1000;
      String result = " ";
      long mins = secs / 60;
      if (mins > 0) {
         result += mins + " mins ";
      }
      result += (secs % 60) + " secs ";
      return result.trim();
   }
}
