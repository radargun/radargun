package org.radargun.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class Log4j2Log implements Log {
   private transient Logger logger;
   private Class<?> clazz;
   private String className;

   public Log4j2Log(Class<?> clazz) {
      this.clazz = clazz;
   }

   public Log4j2Log(String className) {
      this.className = className;
   }

   private Logger getLogger() {
      if (logger == null) {
         if (clazz != null) {
            logger = LogManager.getLogger(clazz);
         } else if (className != null) {
            logger = LogManager.getLogger(className);
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
      return getLogger().isWarnEnabled();
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
      return getLogger().isErrorEnabled();
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
      return getLogger().isFatalEnabled();
   }

   @Override
   public Level getLevel() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void setLevel(Level level) {
      throw new UnsupportedOperationException();
   }
}
