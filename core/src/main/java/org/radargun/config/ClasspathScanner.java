package org.radargun.config;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.Consumer;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Helper for listing classes on classpath.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class ClasspathScanner {
   private static final Log log = LogFactory.getLog(ClasspathScanner.class);

   private ClasspathScanner() {
   }

   @SuppressWarnings("unchecked")
   public static <TClass, TAnnotation extends Annotation> void scanClasspath(Class<TClass> superClass,
                                                                             Class<TAnnotation> annotationClass,
                                                                             String requirePackage,
                                                                             Consumer<Class<? extends TClass>> consumer) {
      
      FastClasspathScanner fcs;
      if (requirePackage != null) {
         fcs = new FastClasspathScanner(requirePackage, superClass.getName());
      } else {
         fcs = new FastClasspathScanner(superClass.getName());
      }
      
      List<String> matches = fcs.scan()
            .getNamesOfClassesWithAnnotation(annotationClass.getName());
      for (String className : matches) {
         Class<?> clazz;
         try {
            clazz = Class.forName(className);
            consumer.accept((Class<? extends TClass>) clazz);
         } catch (Throwable e) {
            // static ctor can throw non-wrapped error
            log.error("Cannot load class " + className, e);
            continue;
         }
      }
   }
}
