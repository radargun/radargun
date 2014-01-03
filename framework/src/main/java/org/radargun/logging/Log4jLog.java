package org.radargun.logging;

import org.apache.log4j.Logger;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Log4jLog implements Log {
   private transient Logger logger;
   private Class<?> clazz;
   private String className;

   public Log4jLog(Class<?> clazz) {
      this.clazz = clazz;
   }

   public Log4jLog(String className) {
      this.className = className;
   }

   private Logger getLogger() {
      if (logger == null) {
         if (clazz != null) {
            logger = Logger.getLogger(clazz);
         } else if (className != null) {
            logger = Logger.getLogger(className);
         }
      }
      return logger;
   }

   @Override
   public final void trace(String message) {
      getLogger().trace(message);
   }

   @Override
   public final void trace(String message, Throwable throwable) {
      getLogger().trace(message, throwable);
   }

   @Override
   public final boolean isTraceEnabled() {
      return getLogger().isTraceEnabled();
   }

   @Override
   public final void debug(String message) {
      getLogger().debug(message);
   }

   @Override
   public final void debug(String message, Throwable throwable) {
      getLogger().debug(message, throwable);
   }

   @Override
   public final boolean isDebugEnabled() {
      return getLogger().isDebugEnabled();
   }

   @Override
   public final void info(String message) {
      getLogger().info(message);
   }

   @Override
   public final void info(String message, Throwable throwable) {
      getLogger().info(message, throwable);
   }

   @Override
   public final boolean isInfoEnabled() {
      return getLogger().isInfoEnabled();
   }

   @Override
   public final void warn(String message) {
      getLogger().warn(message);
   }

   @Override
   public final void warn(String message, Throwable throwable) {
      getLogger().warn(message, throwable);
   }

   @Override
   public final boolean isWarnEnabled() {
      return true;
   }

   @Override
   public final void error(String message) {
      getLogger().error(message);
   }

   @Override
   public final void error(String message, Throwable throwable) {
      getLogger().error(message, throwable);
   }

   @Override
   public final boolean isErrorEnabled() {
      return true;
   }

   @Override
   public final void fatal(String message) {
      getLogger().fatal(message);
   }

   @Override
   public final void fatal(String message, Throwable throwable) {
      getLogger().fatal(message, throwable);
   }

   @Override
   public final boolean isFatalEnabled() {
      return true;
   }

   @Override
   public Level getLevel() {
      return Level.toLevel(getLogger().getLevel().toString());
   }

   @Override
   public void setLevel(Level level) {
      getLogger().setLevel(org.apache.log4j.Level.toLevel(level.name()));
   }
}
