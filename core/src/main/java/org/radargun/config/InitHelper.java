package org.radargun.config;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InitHelper {
   private static final Log log = LogFactory.getLog(InitHelper.class);

   public static void init(Object target) {
      processAnnotatedMethods(target, Init.class, false);
   }

   public static void destroy(Object target) {
      processAnnotatedMethods(target, Destroy.class, true);
   }

   /**
    * Looks for annotated methods of given type in the class hierarchy of given object and invokes them in specified
    * order. The behavior is as follows:
    *
    * <ol>
    *    <li>1. If overriden methods are detected, only the most specific one is invoked. This is generally aplicable.</li>
    *    <li>2. If superclass contains annotated method, which is overriden in a sublclass and overriding method is not annotated,
    *    invoke subclass method in place of superclass method (respecting superclass method priority).</li>
    *    <li>2. If superclass contains annotated method, which is overriden in a sublclass and overriding method is annotated,
    *    invoke subclass method only (respecting subclass method priority). Superclass method is not invoked</li>
    * </ol>
    *
    * e.g.
    * class A {
    *    @Init
    *    void foo() {
    *       System.out.print("A");
    *    }
    * }
    *
    * class B extends A {
    *    @Init
    *    void fooB() {
    *       System.out.print("B");
    *    }
    * }
    *
    * class C extends A {
    *    @Override
    *    void foo() {
    *       System.out.print("C");
    *    }
    * }
    *
    *class D extends B {
    *    @Init
    *    @Override
    *    void foo() {
    *       System.out.print("D");
    *    }
    * }
    *
    * <ul>
    *    <li>Invoking processAnnotatedMethods(new A(), Init.class, false) prints "A"</li>
    *    <li>Invoking processAnnotatedMethods(new B(), Init.class, false) prints "AB"</li>
    *    <li>Invoking processAnnotatedMethods(new C(), Init.class, false) prints "CB"</li>
    *    <li>Invoking processAnnotatedMethods(new D(), Init.class, false) prints "BD"</li>
    * </ul>
    *
    * @param target Object to invoke annotated methods on
    * @param annotationClass Target annotation class to look for
    * @param specializedClassFirst If set to true, annotated methods will be invoked in bottom-up approach
    */
   private static void processAnnotatedMethods(Object target, Class annotationClass, boolean specializedClassFirst) {
      if (target == null) throw new NullPointerException();
      LinkedList<Method> annotatedMethods = new LinkedList<>();
      Class<?> clazz = target.getClass();
      while (clazz != null) {
         for (Method m : clazz.getDeclaredMethods()) {
            if (m.getAnnotation(annotationClass) != null) {
               boolean overridden = false;
               for (Method m2 : annotatedMethods) {
                  if (m2.getName().equals(m.getName())
                        && Arrays.equals(m2.getGenericParameterTypes(), m.getGenericParameterTypes())) {
                     log.warnf("Method %s overrides %s but both are declared with @%s annotation: calling only once", m2, m, annotationClass.getSimpleName());
                     overridden = true;
                  }
               }
               m.setAccessible(true);
               if (!overridden) {
                  annotatedMethods.add(m);
               }
            }
         }
         clazz = clazz.getSuperclass();
      }
      Iterator<Method> iterator = specializedClassFirst ? annotatedMethods.listIterator() : annotatedMethods.descendingIterator();
      while (iterator.hasNext()) {
         try {
            iterator.next().invoke(target);
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }
   }
}
