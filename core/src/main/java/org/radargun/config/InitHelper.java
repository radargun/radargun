package org.radargun.config;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Stack;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InitHelper {

   private static final Log log = LogFactory.getLog(InitHelper.class);

   public static void init(Object target) {
      processAnnotatedMethods(target, Init.class);
   }

   public static void destroy(Object target) {
      processAnnotatedMethods(target, Destroy.class);
   }

   private static void processAnnotatedMethods(Object target, Class annotationClass) {
      if (target == null) throw new NullPointerException();
      Stack<Method> inits = new Stack<>();
      Class<?> clazz = target.getClass();
      while (clazz != null) {
         for (Method m : clazz.getDeclaredMethods()) {
            if (m.getAnnotation(annotationClass) != null) {
               boolean overridden = false;
               for (Method m2 : inits) {
                  if (m2.getName().equals(m.getName())
                        && Arrays.equals(m2.getGenericParameterTypes(), m.getGenericParameterTypes())) {
                     log.warnf("Method %s overrides %s but both are declared with @%s annotation: calling only once", m2, m, annotationClass.getSimpleName());
                     overridden = true;
                  }
               }
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
}
