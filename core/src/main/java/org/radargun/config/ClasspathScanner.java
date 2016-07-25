package org.radargun.config;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.scanner.ClassInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * 
 * Helper for listing classes on classpath.
 *
 */
public final class ClasspathScanner {
   private static final Log log = LogFactory.getLog(ClasspathScanner.class);

   private ClasspathScanner() {
   }

   @SuppressWarnings("unchecked")
   /**
    * 
    * Scan the classpath for classes with the specified annotations
    * 
    * @param superClass
    *           restrict the search to annotations that are subclasses of this class, or
    *           <code>null</code> to search all classes
    * @param annotationClass
    *           the annotation to find. Required.
    * @param requirePackage
    *           restrict the search for the annotation to this package, or <code>null</code> to
    *           search all packages
    * @param consumer
    *           a Consumer to process the matching annotion classes
    */
   public static <TClass, TAnnotation extends Annotation> void scanClasspath(Class<TClass> superClass,
                                                                             Class<TAnnotation> annotationClass,
                                                                             String requirePackage,
                                                                             Consumer<Class<? extends TClass>> consumer) {

      if (annotationClass == null) {
         throw new IllegalArgumentException("An annotation class must be specified");
      }
      FastClasspathScanner fcs;
      if (requirePackage != null) {
         fcs = new FastClasspathScanner("!", requirePackage);
      } else {
         fcs = new FastClasspathScanner("!");
      }

      ScanResult scanResults = fcs.scan();

      List<String> matches = Collections.EMPTY_LIST;
      if (superClass != null) {
         matches = scanResults.getClassNameToClassInfo().values().stream()
               .filter(ci -> ci.hasSuperclass(superClass.getName()) || ci.implementsInterface(superClass.getName()))
               .filter(ci -> ci.hasAnnotation(annotationClass.getName())).map(ClassInfo::getClassName)
               .collect(Collectors.toList());
         log.debug("Found " + matches.size() + " classes with annotation '" + annotationClass.getName()
               + "' and super class '" + superClass.getName() + "'");
      } else {
         matches = scanResults.getClassNameToClassInfo().values().stream()
               .filter(ci -> ci.hasAnnotation(annotationClass.getName())).map(ClassInfo::getClassName)
               .collect(Collectors.toList());
         log.debug("Found " + matches.size() + " classes with annotation '" + annotationClass.getName() + "'");
      }

      // Only load matched classes to avoid any spourious exceptions thrown from static blocks
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