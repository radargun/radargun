package org.radargun.logging;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public enum Level {
   TRACE, DEBUG, INFO, WARN, ERROR, FATAL;

   public static Level toLevel(String level) {
      return Level.valueOf(level.trim().toUpperCase());
   }
}
