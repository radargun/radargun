package org.radargun.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author Mircea.Markus@jboss.com
 */
public class Utils {

   private static Log log = LogFactory.getLog(Utils.class);
   public static final String PLUGINS_DIR = "plugins";
   private static final NumberFormat NF = new DecimalFormat("##,###");
   private static final NumberFormat MEM_FMT = new DecimalFormat("##,###.##");

   public static String getMillisDurationString(long millis) {
      long secs = millis / 1000;
      long mins = secs / 60;
      long remainingSecs = secs % 60;
      if (mins > 0) {
         return String.format("%d mins %d secs", mins, remainingSecs);
      }
      else {
         return String.format("%.3f secs", millis / 1000.0);
      }
   }

   public static String getNanosDurationString(long nanos) {
      return getMillisDurationString(nanos / 1000000);
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
      String memoryInfo = "Memory - free: " + kbString(run.freeMemory()) + " - max:" + kbString(run.maxMemory()) + "- total:" + kbString(run.totalMemory());
      if (before) {
         return "Before executing clear, memory looks like this: " + memoryInfo;
      } else {
         return "After executing cleanup, memory looks like this: " + memoryInfo;
      }
   }

   public static long getFreeMemoryKb() {
      return kb(Runtime.getRuntime().freeMemory());
   }

   private static String format(long bytes) {
      double val = bytes;
      int mag = 0;
      while (val > 1024) {
         val = val / 1024;
         mag++;
      }

      String formatted = MEM_FMT.format(val);
      switch (mag) {
         case 0:
            return formatted + " bytes";
         case 1:
            return formatted + " kb";
         case 2:
            return formatted + " Mb";
         case 3:
            return formatted + " Gb";
         default:
            return "WTF?";
      }
   }

   public static long kb(long memBytes) {
      return memBytes / 1024;
   }

   public static String kbString(long memBytes) {
      return MEM_FMT.format(memBytes / 1024) + " kb";
   }

   public static String memString(long mem, String suffix) {
      return MEM_FMT.format(mem) + " " + suffix;
   }

   public static String memString(long memInBytes) {
      return format(memInBytes);
   }

   public static URLClassLoader buildProductSpecificClassLoader(String productName, ClassLoader parent) throws Exception {
      log.trace("Using smart class loading");
      File libFolder = new File(PLUGINS_DIR + File.separator + productName + File.separator + "lib");
      List<URL> jars = new ArrayList<URL>();
      if (!libFolder.isDirectory()) {
         log.info("Could not find lib directory: " + libFolder.getAbsolutePath());
      } else {
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
         for (String file : jarsSrt) {
            File aJar = new File(libFolder, file);
            if (!aJar.exists() || !aJar.isFile()) {
               throw new IllegalStateException();
            }
            jars.add(aJar.toURI().toURL());
         }
      }
      File confDir = new File(PLUGINS_DIR + File.separator + productName + File.separator + "conf/");
      jars.add(confDir.toURI().toURL());
      return new URLClassLoader(jars.toArray(new URL[jars.size()]), parent);
   }

   public static String getCacheProviderProperty(String productName, String propertyName) {
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
         return properties.getProperty(propertyName);
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

   public static String getCacheWrapperFqnClass(String productName) {
      return Utils.getCacheProviderProperty(productName, "org.radargun.wrapper");
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
         String extension = lastIndexOfDot > 0 ? outputFile.getName().substring(lastIndexOfDot) : "";
         File old = new File(outputFile.getParentFile(), "old");
         if (!old.exists()) {
            if (old.mkdirs()) {
               log.warn("Issues whilst creating dir: " + old);
            }
         }
         String fileName = outputFile.getName() + ".old." + System.currentTimeMillis() + extension;
         File newFile = new File(old, fileName);
         log.info("A file named: '" + outputFile.getAbsolutePath() + "' already exists. Moving it to '" + newFile + "'");
         if (!outputFile.renameTo(newFile)) {
            log.warn("Could not rename!!!");
         }
      }
   }

   public static String prettyPrintTime(long time, TimeUnit unit) {
      return prettyPrintMillis(unit.toMillis(time));
   }

   /**
    * Prints a time for display
    *
    * @param millis time in millis
    * @return the time, represented as millis, seconds, minutes or hours as appropriate, with suffix
    */
   public static String prettyPrintMillis(long millis) {
      if (millis < 1000) return millis + " milliseconds";
      NumberFormat nf = NumberFormat.getNumberInstance();
      nf.setMaximumFractionDigits(2);
      double toPrint = ((double) millis) / 1000;
      if (toPrint < 300) {
         return nf.format(toPrint) + " seconds";
      }

      toPrint = toPrint / 60;

      if (toPrint < 120) {
         return nf.format(toPrint) + " minutes";
      }

      toPrint = toPrint / 60;

      return nf.format(toPrint) + " hours";
   }

   public static void seep(long duration) {
      try {
         Thread.sleep(duration);
      } catch (InterruptedException e) {
         throw new IllegalStateException(e);
      }
   }

   public static String numberFormat(int i) {
      return NF.format(i);
   }

   public static String numberFormat(double d) {
      return NF.format(d);
   }

   public static Object instantiate(String name) {
      try {
         return Class.forName(name).newInstance();
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }
   
   public static long string2Millis(String duration) {
      long durationMillis = 0;
      try {
         durationMillis = Long.parseLong(duration);
      } catch (NumberFormatException nfe) {
         int indexOfM = duration.toUpperCase().indexOf('M');
         if (indexOfM > 0) {
            durationMillis = Long.parseLong(duration.substring(0, indexOfM));
            durationMillis = TimeUnit.MINUTES.toMillis(durationMillis);
         } else {
            int indexOfS = duration.toUpperCase().indexOf('S');
            if (indexOfS > 0) {
               durationMillis = Long.parseLong(duration.substring(0, indexOfS));
               durationMillis = TimeUnit.SECONDS.toMillis(durationMillis);
            }
            else {
               throw new IllegalArgumentException("Cannot parse string: '" + duration + "' Supported formats: '321321' (millis), '3m' (minutes) or '75s' (seconds)");
            }
         }
      }
      return durationMillis;
   }
}
