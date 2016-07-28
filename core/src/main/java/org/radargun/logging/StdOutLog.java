package org.radargun.logging;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Log with level fixed to INFO, writing to stdout.
 */
public class StdOutLog implements Log {
   private final String className;
   private final ThreadLocal<SimpleDateFormat> dateTimeFormatter = new ThreadLocal<SimpleDateFormat>() {
      @Override
      protected SimpleDateFormat initialValue() {
         return new SimpleDateFormat("HH:mm:ss.SSS");
      }
   };

   public StdOutLog(String className) {
      this.className = className;
   }

   private String getTime() {
      return dateTimeFormatter.get().format(new Date());
   }

   @Override
   public void trace(String message) {
   }

   @Override
   public void trace(String message, Throwable throwable) {
   }

   @Override
   public void tracef(String format, Object... args) {
   }

   @Override
   public void tracef(Throwable throwable, String format, Object... args) {
   }

   @Override
   public boolean isTraceEnabled() {
      return false;
   }

   @Override
   public void debug(String message) {
   }

   @Override
   public void debug(String message, Throwable throwable) {
   }

   @Override
   public void debugf(String format, Object... args) {
   }

   @Override
   public void debugf(Throwable throwable, String format, Object... args) {
   }

   @Override
   public boolean isDebugEnabled() {
      return false;
   }

   @Override
   public void info(String message) {
      System.out.printf("INFO\t%s [%s] (%s) %s\n", getTime(), className, Thread.currentThread().getName(), message);
   }

   @Override
   public void info(String message, Throwable throwable) {
      System.out.printf("INFO\t%s [%s] (%s) %s\n", getTime(), className, Thread.currentThread().getName(), message);
      throwable.printStackTrace(System.out);
   }

   @Override
   public void infof(String format, Object... args) {
      System.out.printf("INFO\t%s [%s] (%s) " + format + "\n", prefix(args));
   }

   @Override
   public void infof(Throwable throwable, String format, Object... args) {
      System.out.printf("INFO\t%s [%s] (%s) " + format + "\n", prefix(args));
      throwable.printStackTrace(System.out);
   }

   @Override
   public boolean isInfoEnabled() {
      return true;
   }

   @Override
   public void warn(String message) {
      System.out.printf("WARN\t%s [%s] (%s) %s\n", getTime(), className, Thread.currentThread().getName(), message);
   }

   @Override
   public void warn(String message, Throwable throwable) {
      System.out.printf("WARN\t%s [%s] (%s) %s\n", getTime(), className, Thread.currentThread().getName(), message);
      throwable.printStackTrace(System.out);
   }

   @Override
   public void warnf(String format, Object... args) {
      System.out.printf("WARN\t%s [%s] (%s) " + format + "\n", prefix(args));
   }

   @Override
   public void warnf(Throwable throwable, String format, Object... args) {
      System.out.printf("WARN\t%s [%s] (%s) " + format + "\n", prefix(args));
      throwable.printStackTrace(System.out);
   }

   @Override
   public boolean isWarnEnabled() {
      return true;
   }

   @Override
   public void error(String message) {
      System.out.printf("ERROR\t%s [%s] (%s) %s\n", getTime(), className, Thread.currentThread().getName(), message);
   }

   @Override
   public void error(String message, Throwable throwable) {
      System.out.printf("ERROR\t%s [%s] (%s) %s\n", getTime(), className, Thread.currentThread().getName(), message);
      throwable.printStackTrace(System.out);
   }

   @Override
   public void errorf(String format, Object... args) {
      System.out.printf("ERROR\t%s [%s] (%s) " + format + "\n", prefix(args));
   }

   @Override
   public void errorf(Throwable throwable, String format, Object... args) {
      System.out.printf("ERROR\t%s [%s] (%s) " + format + "\n", prefix(args));
      throwable.printStackTrace(System.out);
   }

   @Override
   public boolean isErrorEnabled() {
      return true;
   }

   @Override
   public void fatal(String message) {
      System.out.printf("FATAL\t%s [%s] (%s) %s\n", getTime(), className, Thread.currentThread().getName(), message);
   }

   @Override
   public void fatal(String message, Throwable throwable) {
      System.out.printf("FATAL\t%s [%s] (%s) %s\n", getTime(), className, Thread.currentThread().getName(), message);
      throwable.printStackTrace(System.out);
   }

   @Override
   public void fatalf(String format, Object... args) {
      System.out.printf("FATAL\t%s [%s] (%s) " + format + "\n", prefix(args));
   }

   @Override
   public void fatalf(Throwable throwable, String format, Object... args) {
      System.out.printf("FATAL\t%s [%s] (%s) " + format + "\n", prefix(args));
      throwable.printStackTrace(System.out);
   }

   @Override
   public boolean isFatalEnabled() {
      return true;
   }

   @Override
   public Level getLevel() {
      return Level.INFO;
   }

   @Override
   public void setLevel(Level level) {
      throw new UnsupportedOperationException();
   }

   private Object[] prefix(Object[] args) {
      return new Object[] {getTime(), className, Thread.currentThread().getName(), args};
   }

}
