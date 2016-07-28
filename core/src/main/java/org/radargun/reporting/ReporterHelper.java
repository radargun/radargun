package org.radargun.reporting;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.radargun.Directories;
import org.radargun.config.Definition;
import org.radargun.config.InitHelper;
import org.radargun.config.PropertyHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.ArgsHolder;
import org.radargun.utils.Utils;

/**
 * Helper class creating reporters (in their own classloaders).
 * Each reporter module (directory) should contain JAR with file plugin.properties.
 * This file lists all reporter as reporter./reporter type/ /reporter class/
 */
public final class ReporterHelper {
   private static final String REPORTER_PREFIX = "reporter.";

   private static final Log log = LogFactory.getLog(ReporterHelper.class);
   private static final Map<String, Class<? extends Reporter>> REPORTERS = new HashMap<>();

   static {
      for (File reporterDir : Directories.REPORTERS_DIR.listFiles()) {
         loadReporters(reporterDir, REPORTERS);
      }
      for (String path : ArgsHolder.getReporterPaths()) {
         loadReporters(new File(path), REPORTERS);
      }
   }

   private ReporterHelper() {}

   public static void registerReporters(String reporterDir) {
      loadReporters(new File(reporterDir), REPORTERS);
   }

   public static void loadReporters(File reporterDir, Map<String, Class<? extends Reporter>> reporters) {
      try {
         if (!reporterDir.isDirectory()) {
            log.warn(reporterDir + " is not a directory");
            return;
         }
         List<URL> urls = new ArrayList<>();
         Properties properties = null;
         for (File jar : reporterDir.listFiles(new Utils.JarFilenameFilter())) {
            urls.add(jar.toURI().toURL());
            if (properties == null) {
               properties = Utils.getJarProperties(jar);
            }
         }
         if (urls.isEmpty()) {
            log.warn("No JARs in " + reporterDir);
            return;
         }
         ClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), ReporterHelper.class.getClassLoader());
         for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(REPORTER_PREFIX)) {
               String type = propertyName.substring(REPORTER_PREFIX.length());
               String clazzName = properties.getProperty(propertyName);
               if (reporters.containsKey(type)) {
                  throw new IllegalStateException("Reporter for type " + type + " already registered: " + reporters.get(type));
               } else {
                  log.debug("Registering reporter '" + type + "' using " + clazzName);
                  try {
                     Class<? extends Reporter> reporterClazz = (Class<? extends Reporter>) classLoader.loadClass(clazzName);
                     reporters.put(type, reporterClazz);
                  } catch (ClassNotFoundException e) {
                     log.error("Failed to load reporter class " + clazzName + " for type '" + type + "'");
                  }
               }
            }
         }
      } catch (IOException e) {
         throw new RuntimeException("Failed to load properties from " + reporterDir, e);
      }
   }

   /**
    * Searches for a reporter with particular name.
    * @param type Name of the reporter
    * @return True if reporter with this name was found.
    */
   public static boolean isRegistered(String type) {
      return REPORTERS.containsKey(type);
   }

   /**
    * Create a reporter with given type
    * @param type
    * @param properties
    * @return
    */
   public static Reporter createReporter(String type, Map<String, Definition> properties) {
      Class<? extends Reporter> clazz = REPORTERS.get(type);
      if (clazz == null) {
         throw new IllegalArgumentException("Reporter for type '" + type + "' was not found");
      }
      try {
         Reporter instance = clazz.newInstance();
         PropertyHelper.setPropertiesFromDefinitions(instance, properties, false, true);
         InitHelper.init(instance);
         return instance;
      } catch (Exception e) {
         throw new RuntimeException("Cannot create reporter with class " + clazz, e);
      }
   }

   public static Set<String> getReporterNames() {
      return Collections.unmodifiableSet(REPORTERS.keySet());
   }
}
