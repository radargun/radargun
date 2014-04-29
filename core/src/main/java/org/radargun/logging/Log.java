package org.radargun.logging;

import java.io.Serializable;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Log extends Serializable {

   void trace(String message);
   void trace(String message, Throwable throwable);
   boolean isTraceEnabled();
   void debug(String message);
   void debug(String message, Throwable throwable);
   boolean isDebugEnabled();
   void info(String message);
   void info(String message, Throwable throwable);
   boolean isInfoEnabled();
   void warn(String message);
   void warn(String message, Throwable throwable);
   boolean isWarnEnabled();
   void error(String message);
   void error(String message, Throwable throwable);
   boolean isErrorEnabled();
   void fatal(String message);
   void fatal(String message, Throwable throwable);
   boolean isFatalEnabled();
   Level getLevel();
   void setLevel(Level level);
}
