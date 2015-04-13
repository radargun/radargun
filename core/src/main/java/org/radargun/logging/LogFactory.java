package org.radargun.logging;

/**
 * Common wrapper for any logging system used for logging.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class LogFactory {

   private static final boolean IS_LOG4J_AVAILABLE;
   private static final boolean IS_LOG4J2_AVAILABLE;

   static {
      IS_LOG4J_AVAILABLE=isAvailable("org.apache.log4j.Logger");
      IS_LOG4J2_AVAILABLE=isAvailable("org.apache.logging.log4j.core.Logger");
   }

   private static boolean isAvailable(String classname) {
      try {
         return Class.forName(classname) != null;
      } catch(ClassNotFoundException cnfe) {
         return false;
      }
   }

   public static Log getLog(Class<?> clazz) {
      if (IS_LOG4J2_AVAILABLE) return new Log4j2Log(clazz);
      if (IS_LOG4J_AVAILABLE) return new Log4jLog(clazz);
      return new StdOutLog(clazz.getName());
   }

   public static Log getLog(String className) {
      if (IS_LOG4J2_AVAILABLE) return new Log4j2Log(className);
      if (IS_LOG4J_AVAILABLE) return new Log4jLog(className);
      return new StdOutLog(className);
   }
}
