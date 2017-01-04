package org.radargun.config;

import java.io.File;
import java.util.Vector;

import io.github.lukehutch.fastclasspathscanner.classloaderhandler.ClassLoaderHandler;
import io.github.lukehutch.fastclasspathscanner.scanner.ClasspathFinder;
import io.github.lukehutch.fastclasspathscanner.utils.ReflectionUtils;

/**
 * Allows loading classes from AntClassLoader
 */
public class AntClassLoaderHandler implements ClassLoaderHandler {
   @Override
   public boolean handle(ClassLoader classloader, ClasspathFinder classpathFinder) throws Exception {
      for (Class<?> c = classloader.getClass(); c != null; c = c.getSuperclass()) {
         if ("org.apache.tools.ant.AntClassLoader".equals(c.getName())) {
            Object pathComponents = ReflectionUtils.getFieldVal(classloader, "pathComponents");
            if (pathComponents != null && pathComponents instanceof Vector) {
               for (File file : ((Vector<File>) pathComponents)) {
                  classpathFinder.addClasspathElement(file.getAbsolutePath());
               }
            }
            return true;
         }
      }
      return false;
   }
}
