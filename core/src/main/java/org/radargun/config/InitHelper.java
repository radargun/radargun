package org.radargun.config;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Stack;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InitHelper {
   private static final Log log = LogFactory.getLog(InitHelper.class);

   public static void init(Object target) {
      if (target == null) throw new NullPointerException();
      Stack<Method> inits = new Stack<Method>();
      Class<?> clazz = target.getClass();
      while (clazz != null) {
         for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Init.class)) {
               boolean overridden = false;
               for (Method m2 : inits) {
                  if (m2.getName().equals(m.getName())
                        && Arrays.equals(m2.getGenericParameterTypes(), m.getGenericParameterTypes())) {
                     log.warnf("Method %s overrides %s but both are declared with @Init annotation: calling only once", m2, m);
                     overridden = true;
                  }
               }
               m.setAccessible(true);
               if (!overridden) {
                  inits.push(m);
               }
            }
         }
         clazz = clazz.getSuperclass();
      }
      while (!inits.isEmpty()) {
         try {
            inits.pop().invoke(target);
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }
   }

   public static void destroy(Object target) {
      if (target == null) throw new NullPointerException();
      Class<?> clazz = target.getClass();
      while (clazz != null) {
         for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Destroy.class)) {
               m.setAccessible(true);
               try {
                  m.invoke(target);
               } catch (Exception e) {
                  throw new RuntimeException(e);
               }
            }
         }
         clazz = clazz.getSuperclass();
      }
   }
}
