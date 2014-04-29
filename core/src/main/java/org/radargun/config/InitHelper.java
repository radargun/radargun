package org.radargun.config;

import java.lang.reflect.Method;
import java.util.Stack;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InitHelper {
   public static void init(Object target) {
      if (target == null) throw new NullPointerException();
      Stack<Method> inits = new Stack<Method>();
      Class<?> clazz = target.getClass();
      while (clazz != null) {
         for (Method m : clazz.getDeclaredMethods()) {
            if (m.getAnnotation(Init.class) != null) {
               inits.push(m);
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
