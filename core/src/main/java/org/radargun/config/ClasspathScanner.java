package org.radargun.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Helper for listing classes on classpath.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class ClasspathScanner {
   private static final Log log = LogFactory.getLog(ClasspathScanner.class);
   private static final String CLASS_SUFFIX = ".class";
   private static final String JAR_SUFFIX = ".jar";
   private static final PrintStream NULL_PRINT_STREAM = new PrintStream(new OutputStream() {
      @Override
      public void write(int b) throws IOException {}
   });
   private static final PrintStream ERR_PRINT_STREAM = System.err;

   private ClasspathScanner() {}

   public static <TClass, TAnnotation extends Annotation> void scanClasspath(
         Class<TClass> superClass, Class<TAnnotation> annotationClass, String requirePackage, Consumer<Class<? extends TClass>> consumer) {
      String classPath = System.getProperty("java.class.path");
      String[] classPathParts = classPath.split(File.pathSeparator);

      for (String resource : classPathParts) {
         File resourceFile = new File(resource);
         if (resourceFile.isFile()) {
            scanFile(resource, 0, superClass, annotationClass, requirePackage, consumer);
         } else if (resourceFile.isDirectory()) {
            int prefixLength = resource.length() + 1;
            try {
               Files.find(resourceFile.toPath(), Integer.MAX_VALUE, (path, attrs) -> attrs.isRegularFile() && (path.toString().endsWith(CLASS_SUFFIX) || path.toString().endsWith(JAR_SUFFIX))).forEach(file -> {
                  scanFile(file.toString(), prefixLength, superClass, annotationClass, requirePackage, consumer);
               });
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
      }
   }

   protected static <TClass, TAnnotation extends Annotation> void scanFile(String resource, int prefixLength,
         Class<TClass> superClass, Class<TAnnotation> annotationClass, String requirePackage, Consumer<Class<? extends TClass>> consumer) {
      if (resource.endsWith(".jar")) {
         scanJar(resource, superClass, annotationClass, requirePackage, consumer);
      } else if (resource.endsWith(CLASS_SUFFIX)) {
         String className = resource.substring(prefixLength, resource.length() - CLASS_SUFFIX.length()).replaceAll("/", ".");
         scanClass(className, superClass, annotationClass, requirePackage, consumer);
      }
   }

   public static <TClass, TAnnotation extends Annotation> void scanJar(String path,
         Class<TClass> superClass, Class<TAnnotation> annotationClass, String requirePackage, Consumer<Class<? extends TClass>> consumer) {
      log.tracef("Looking for @%s %s, loading classes from %s", annotationClass.getSimpleName(), superClass.getSimpleName(), path);
      try (ZipInputStream inputStream = new ZipInputStream(new FileInputStream(path))) {
         for(;;) {
            ZipEntry entry = inputStream.getNextEntry();
            if (entry == null) break;
            if (!entry.getName().endsWith(CLASS_SUFFIX)) continue;
            String className = entry.getName().replace('/', '.').substring(0, entry.getName().length() - CLASS_SUFFIX.length());
            scanClass(className, superClass, annotationClass, requirePackage, consumer);
         }
      } catch (FileNotFoundException e) {
         log.error("Cannot load executed JAR file '" + path + "'to find stages.");
      } catch (IOException e) {
         log.error("Cannot open/read JAR '" + path + "'");
      }
   }

   protected static <TClass, TAnnotation extends Annotation> void scanClass(String className,
         Class<TClass> superClass, Class<TAnnotation> annotationClass, String requirePackage, Consumer<Class<? extends TClass>> consumer) {
      if (requirePackage != null && !className.startsWith(requirePackage)) return;
      Class<?> clazz;
      try {
         System.setErr(NULL_PRINT_STREAM); // suppress any error output
         clazz = Class.forName(className);
         System.setErr(ERR_PRINT_STREAM);
      } catch (ClassNotFoundException e) {
         log.trace("Cannot load class " + className, e);
         return;
      } catch (NoClassDefFoundError e) {
         log.trace("Cannot load class " + className, e);
         return;
      } catch (LinkageError e) {
         log.trace("Cannot load class " + className, e);
         return;
      }
      TAnnotation annotation = clazz.getAnnotation(annotationClass);
      if (annotation != null) {
         if (!superClass.isAssignableFrom(clazz)) {
            return;
         }
         consumer.accept((Class<? extends TClass>) clazz);
      }
   }
}
