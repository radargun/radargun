package org.radargun.utils;

import javax.management.MBeanServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.GenerateReportStage;

/**
 * @author Mircea.Markus@jboss.com
 */
public class Utils {

   private static Log log = LogFactory.getLog(Utils.class);
   public static final String PLUGINS_DIR = "plugins";
   private static final NumberFormat NF = new DecimalFormat("##,###");
   private static final NumberFormat MEM_FMT = new DecimalFormat("##,###.##");

   private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
   private static final String HOTSPOT_BEAN_CLASS = "com.sun.management.HotSpotDiagnosticMXBean";
   private static final String HOTSPOT_BEAN_DUMP_METHOD = "dumpHeap";

   // field to store the hotspot diagnostic MBean
   private static volatile Object hotspotMBean;

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
               log.warn("Error closing properties stream", e);
            }
      }
   }

   public static String getCacheWrapperFqnClass(String productName) {
      return getCacheProviderProperty(productName, "org.radargun.wrapper");
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
      duration = duration.toUpperCase().trim();
      int days = duration.indexOf('D');
      int hours = duration.indexOf('H');
      int minutes = duration.indexOf('M');
      int seconds = duration.indexOf('S');
      int lastIndex = 0;
      try {
         if (days > 0) {
            durationMillis += TimeUnit.DAYS.toMillis(Long.parseLong(duration.substring(lastIndex, days).trim()));
            lastIndex = days + 1;
         }
         if (hours > 0) {
            durationMillis += TimeUnit.HOURS.toMillis(Long.parseLong(duration.substring(lastIndex, hours).trim()));
            lastIndex = hours + 1;
         }
         if (minutes > 0) {
            durationMillis += TimeUnit.MINUTES.toMillis(Long.parseLong(duration.substring(lastIndex, minutes).trim()));
            lastIndex = minutes + 1;
         }
         if (seconds > 0) {
            durationMillis += TimeUnit.SECONDS.toMillis(Long.parseLong(duration.substring(lastIndex, seconds).trim()));
            lastIndex = seconds + 1;
         }
         if (lastIndex < duration.length()) {
            durationMillis += Long.parseLong(duration.substring(lastIndex));
         }
      } catch (NumberFormatException nfe) {
         throw new IllegalArgumentException("Cannot parse string: '" + duration + "'", nfe);
      }
      return durationMillis;
   }

   public static void createOutputFile(String fileName, String fileContent) throws IOException {
      createOutputFile(fileName, fileContent, true);
   }

   public static void createOutputFile(String fileName, String fileContent, boolean doBackup) throws IOException {
      File parentDir = new File(GenerateReportStage.REPORTS);
      if (!parentDir.exists() && !parentDir.mkdirs()) {
         log.error("Directory '" + parentDir.getAbsolutePath() + "' could not be created");
         /*
          * The file will be created in the Sytem tmp directory
          */
         parentDir = new File(System.getProperty("java.io.tmpdir"));
      }

      File reportFile = new File(parentDir, fileName);
      if (!reportFile.exists() || doBackup) {
         reportFile = Utils.createOrReplaceFile(parentDir, fileName);
      }

      if (!reportFile.exists()) {
         throw new IllegalStateException(reportFile.getAbsolutePath()
               + " was deleted? Not allowed to delete report file during test run!");
      } else {
         log.info("Report file '" + reportFile.getAbsolutePath() + "' created");
      }
      FileWriter writer = null;
      try {
         writer = new FileWriter(reportFile, !doBackup);
         writer.append(fileContent);
      } finally {
         if (writer != null)
            writer.close();
      }
   }

   /**
    * 
    * Parse a string containing method names and String parameters. Multiple method names and
    * parameters are separated by a ';'. Method names and parameters are separated by a ':'.
    * 
    * @return a Map with the method name as the key, and the String parameter as the value, or an
    *         empty Map if <code>parameterString</code> is <code>null</code>
    */
   public static Map<String, String> parseParams(String parameterString) {
      Map<String, String> result = new HashMap<String, String>();
      if (parameterString != null) {
         for (String propAndValue : parameterString.split(";")) {
            String[] values = propAndValue.split(":");
            result.put(values[0].trim(), values[1].trim());
         }
      }
      return result;
   }
   
   /**
    * 
    * Invoke a public method with a String argument on an Object
    * 
    * @param object
    *           the Object where the method is invoked
    * @param properties
    *           a Map where the public method name is the key, and the String parameter is the value
    * @return the modified Object, or <code>null</code> if the field can't be changed
    */
   public static Object invokeMethodWithString(Object object, Map<String, String> properties) {
      Class<? extends Object> clazz = object.getClass();
      for (Entry<String, String> entry : properties.entrySet()) {
         try {
            Method method = clazz.getDeclaredMethod(entry.getKey(), String.class);
            method.invoke(object, entry.getValue());
         } catch (Exception e) {
            log.error("Error invoking method named " + entry.getKey() + " with value " + entry.getValue(), e);
            return null;
         }
      }
      return object;
   }

   public static void dumpHeap(String file) throws Exception {
      if (hotspotMBean == null) {
         synchronized (Utils.class) {
            if (hotspotMBean == null) {
               MBeanServer server = ManagementFactory.getPlatformMBeanServer();
               hotspotMBean =
                     ManagementFactory.newPlatformMXBeanProxy(server, HOTSPOT_BEAN_NAME, HotSpotDiagnosticMXBean.class);
            }
         }
      }
      Class clazz = Class.forName(HOTSPOT_BEAN_CLASS);
      Method m = clazz.getMethod(HOTSPOT_BEAN_DUMP_METHOD, String.class, boolean.class);
      m.invoke(hotspotMBean, file, true);
   }

   public static long getRandomSeed(Random random) {
      try {
         Field seedField = Random.class.getDeclaredField("seed");
         seedField.setAccessible(true);
         return ((AtomicLong) seedField.get(random)).get();
      } catch (Exception e) {
         log.error("Cannot access seed", e);
         throw new RuntimeException(e);
      }
   }

   public static Random setRandomSeed(Random random, long seed) {
      random.setSeed(seed ^ 0x5DEECE66DL);
      return random;
   }

   /**
    * 
    * Sort and save properties to a file.
    * 
    * @param props Properties
    * @param f file
    * @throws Exception
    */
   public static void saveSorted(Properties props, File f) throws Exception {
      FileOutputStream fout = null;
      try {
         Properties sorted = new Properties() {
            @Override
            public Set<Object> keySet() {
               return Collections.unmodifiableSet(new TreeSet<Object>(super.keySet()));
            }

            @Override
            public synchronized Enumeration<Object> keys() {
               return Collections.enumeration(new TreeSet<Object>(super.keySet()));
            }

            @Override
            public Set<String> stringPropertyNames() {
               return Collections.unmodifiableSet(new TreeSet<String>(super.stringPropertyNames()));
            }
         };
         sorted.putAll(props);
         fout = new FileOutputStream(f);
         sorted.storeToXML(fout, null);
         fout.flush();
      } finally {
         if (fout != null) {
            fout.close();
         }
      }
   }
}
