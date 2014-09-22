package org.radargun.logging;

import java.io.Serializable;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Log extends Serializable {

   void trace(String message);
   void trace(String message, Throwable throwable);
   void tracef(String format, Object... args);
   void tracef(Throwable throwable, String format, Object... args);
   boolean isTraceEnabled();
   void debug(String message);
   void debug(String message, Throwable throwable);
   void debugf(String format, Object... args);
   void debugf(Throwable throwable, String format, Object... args);
   boolean isDebugEnabled();
   void info(String message);
   void info(String message, Throwable throwable);
   void infof(String format, Object... args);
   void infof(Throwable throwable, String format, Object... args);
   boolean isInfoEnabled();
   void warn(String message);
   void warn(String message, Throwable throwable);
   void warnf(String format, Object... args);
   void warnf(Throwable throwable, String format, Object... args);
   boolean isWarnEnabled();
   void error(String message);
   void error(String message, Throwable throwable);
   void errorf(String format, Object... args);
   void errorf(Throwable throwable, String format, Object... args);
   boolean isErrorEnabled();
   void fatal(String message);
   void fatal(String message, Throwable throwable);
   void fatalf(String format, Object... args);
   void fatalf(Throwable throwable, String format, Object... args);
   boolean isFatalEnabled();
   Level getLevel();
   void setLevel(Level level);
}
