package org.radargun.logging;

public enum Level {
   TRACE, DEBUG, INFO, WARN, ERROR, FATAL;

   public static Level toLevel(String level) {
      return Level.valueOf(level.trim().toUpperCase());
   }
}
