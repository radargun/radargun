package org.cachebench.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author Mircea.Markus@jboss.com
 */
public class Utils {

   private static Log log = LogFactory.getLog(Utils.class);
   public static final String PLUGINS_DIR = "plugins";

   public static String getDurationString(long duration) {
      long secs = duration / 1000;
      String result = " ";
      long mins = secs / 60;
      if (mins > 0) {
         result += mins + " mins ";
      }
      result += (secs % 60) + " secs ";
      return result.trim();
   }


   public static String fileName2Config(String fileName) {
      int index = fileName.indexOf('.');
      if (index > 0) {
         fileName = fileName.substring(0, index);
         index = fileName.indexOf(File.separator);
         if (index > 0) {
            fileName = fileName.substring(fileName.lastIndexOf(File.separator) + 1);
         }
      }
      return fileName;
   }

   public static String printMemoryFootprint(boolean before) {
      Runtime run = Runtime.getRuntime();
      String memoryInfo = "Memory(KB) - free: " + kb(run.freeMemory()) + " - max:" + kb(run.maxMemory()) + "- total:" + kb(run.totalMemory());
      if (before) {
         return "Before executing clear, memory looks like this: " + memoryInfo;
      } else {
         return "After executing cleanup, memory looks like this: " + memoryInfo;
      }
   }

   public static long kb(long memBytes) {
      return memBytes / 1024;
   }

   public static URLClassLoader buildProductSpecificClassLoader(String productName, ClassLoader parent) throws Exception {
      log.trace("Using smart class laoding");
      File libFolder = new File(PLUGINS_DIR + File.separator + productName + File.separator + "lib");
      if (!libFolder.isDirectory()) {
         String message = "Could not find lib directory: " + libFolder.getAbsolutePath();
         log.error(message);
         throw new IllegalStateException(message);
      }
      String[] jarsSrt = libFolder.list(new FilenameFilter() {
         public boolean accept(File dir, String name) {
            String fileName = name.toUpperCase();
            if (fileName.endsWith("JAR") || fileName.toUpperCase().endsWith("ZIP")) {
               if (log.isTraceEnabled()) {
                  log.trace("Accepting file: " + fileName);
               }
               return true;
            } else {
               if (log.isTraceEnabled()) {
                  log.trace("Rejecting file: " + fileName);
               }
               return false;
            }
         }
      });
      List<URL> jars = new ArrayList<URL>();
      for (String file : jarsSrt) {
         File aJar = new File(libFolder, file);
         if (!aJar.exists() || !aJar.isFile()) {
            throw new IllegalStateException();
         }
         jars.add(aJar.toURI().toURL());
      }
      File confDir = new File(PLUGINS_DIR + File.separator + productName + File.separator + "conf/");
      jars.add(confDir.toURI().toURL());
      URLClassLoader classLoader = new URLClassLoader(jars.toArray(new URL[jars.size()]), parent);
      return classLoader;
   }

   public static String getCacheWrapperFqnClass(String productName) {
      File file = new File(PLUGINS_DIR + File.separator + productName + File.separator + "conf" + File.separator + "cacheprovider.properties");
      if (!file.exists()) {
         log.warn("Could not find a plugin descriptor : " + file);
         return null;
      }
      Properties properties = new Properties();
      FileInputStream inStream = null;
      try {
         inStream = new FileInputStream(file);
         properties.load(inStream);
         return properties.getProperty("org.cachebenchfwk.wrapper");
      } catch (IOException e) {
         throw new RuntimeException(e);
      } finally {
         if (inStream != null)
            try {
               inStream.close();
            } catch (IOException e) {
               log.warn(e);
            }
      }
   }

   public static File createOrReplaceFile(File parentDir, String actualFileName) throws IOException {
      File outputFile = new File(parentDir, actualFileName);

      backupFile(outputFile);
      if (outputFile.createNewFile()) {
         log.info("Successfully created report file:" + outputFile.getAbsolutePath());
      } else {
         log.warn("Failed to create the report file!");
      }
      return outputFile;
   }

   public static void backupFile(File outputFile) {
      if (outputFile.exists()) {
         int lastIndexOfDot = outputFile.getName().lastIndexOf('.');
         String extenssion = lastIndexOfDot > 0 ? outputFile.getName().substring(lastIndexOfDot) : "";
         File old = new File (outputFile.getParentFile(), "old");
         if (old.exists()) {
            if (old.mkdirs()) {
               log.warn("Issues whilst creating dir: " + old);  
            }
         }
         String fileName = outputFile.getAbsolutePath() + ".old." + System.currentTimeMillis() + extenssion;
         File newFile = new File(old, fileName);
         log.info("A file named: '" + outputFile.getAbsolutePath() + "' already exist. Moving it to '" + newFile + "'");
         if (!outputFile.renameTo(newFile)) {
            log.warn("Could not rename!!!");
         }
      }
   }
}
