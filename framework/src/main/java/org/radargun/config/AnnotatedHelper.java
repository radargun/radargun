package org.radargun.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Helper for loading the classes from JAR according to annotations.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 2/19/13
 */
public class AnnotatedHelper {

   private static Log log = LogFactory.getLog(AnnotatedHelper.class);

   public static URL getJAR(Class clazz) {
      return clazz.getProtectionDomain().getCodeSource().getLocation();
   }

   public static <TClass, TAnnotation extends Annotation> List<Class<? extends TClass>>
         getClassesFromJar(String path, Class<TClass> loadedClass, Class<TAnnotation> annotationClass) {
      log.info("Loading JARS from " + path);
      List<Class<? extends TClass>> classes = new ArrayList<Class<? extends TClass>>();
      try {
         ZipInputStream inputStream = new ZipInputStream(new FileInputStream(path));
         for(;;) {
            ZipEntry entry = inputStream.getNextEntry();
            if (entry == null) break;
            if (!entry.getName().endsWith(".class")) continue;
            String className = entry.getName().replace('/', '.').substring(0, entry.getName().length() - 6);
            try {
               Class<?> clazz = Class.forName(className);
               TAnnotation annotation = clazz.getAnnotation(annotationClass);
               if (annotation != null) {
                  if (!loadedClass.isAssignableFrom(clazz)) {
                     log.warn(clazz.getName() + " is marked with " + annotationClass.getSimpleName() + " but does not implement/extend " + loadedClass);
                     continue;
                  }
                  classes.add((Class<? extends TClass>) clazz);
               }
            } catch (ClassNotFoundException e) {
               log.warn("Cannot instantiate class object for " + className);
            }
         }
      } catch (FileNotFoundException e) {
         log.error("Cannot load executed JAR file '" + path + "'to find stages.");
      } catch (IOException e) {
         log.error("Cannot open/read JAR '" + path + "'");
      }
      return classes;
   }
}
