package org.radargun.utils;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.MBeanServer;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.radargun.Directories;
import org.radargun.config.InitHelper;
import org.radargun.config.PropertyHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class Utils {


   private static Log log = LogFactory.getLog(Utils.class);
   private static final String PROPERTIES_FILE = "plugin.properties";
   private static final NumberFormat NF = new DecimalFormat("##,###");
   private static final NumberFormat MEM_FMT = new DecimalFormat("##,###.##");

   private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
   private static final String HOTSPOT_BEAN_CLASS = "com.sun.management.HotSpotDiagnosticMXBean";
   private static final String HOTSPOT_BEAN_DUMP_METHOD = "dumpHeap";

   // field to store the hotspot diagnostic MBean
   private static volatile Object hotspotMBean;

   private Utils() {
   }

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

   public static String getMemoryInfo() {
      Runtime run = Runtime.getRuntime();
      StringBuilder memoryInfo = new StringBuilder();
      memoryInfo.append("Runtime free: ").append(kbString(run.freeMemory()))
            .append("\nRuntime max:").append(kbString(run.maxMemory()))
            .append("\nRuntime total:").append(kbString(run.totalMemory()));
      Iterator<MemoryPoolMXBean> iter = ManagementFactory.getMemoryPoolMXBeans().iterator();
      while (iter.hasNext())
      {
         MemoryPoolMXBean item = iter.next();
         MemoryUsage usage = item.getUsage();
         memoryInfo.append("\nMX ").append(item.getName()).append("(").append(item.getType()).append("): ")
               .append("used: ").append(kbString(usage.getUsed()))
               .append(", init: ").append(kbString(usage.getInit()))
               .append(", committed: ").append(kbString(usage.getCommitted()))
               .append(", max: ").append(kbString(usage.getMax()));
      }
      return memoryInfo.toString();
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

   public static String kbString(long memBytes) {
      return MEM_FMT.format(memBytes / 1024) + " kb";
   }

   public static URLClassLoader buildPluginSpecificClassLoader(String plugin, ClassLoader parent) {
      log.trace("Using smart class loading");
      List<URL> jars = new ArrayList<>();
      try {
         ArgsHolder.PluginParam param = ArgsHolder.getPluginParams().get(plugin);
         if (param != null) {
            String pluginPath = param.getPath();
            if (pluginPath != null) {
               File pluginRoot = new File(pluginPath);
               jars.add(new File(pluginRoot, "conf").toURI().toURL());
               addJars(new File(pluginRoot, "lib"), jars);
            }
            for (String configFile : param.getConfigFiles()) {
               File pluginConfFile = new File(configFile);
               if (pluginConfFile.exists()) {
                  jars.add(new File(pluginConfFile.getParent()).toURI().toURL());
               } else {
                  log.warn("File '" + pluginConfFile + "' does not exist!");
               }
            }
         }
         File pluginDir = new File(Directories.PLUGINS_DIR, plugin);
         if (pluginDir.exists() && pluginDir.isDirectory()) {
            File confDir = new File(pluginDir, "conf");
            jars.add(confDir.toURI().toURL());
            // add default lib only if it is not defined explicitly
            if (param == null || (param != null && param.getPath() == null)) {
               addJars(new File(pluginDir, "lib"), jars);
            }
         }
         addJars(Directories.SPECIFIC_DIR, jars);
      } catch (MalformedURLException e) {
         throw new IllegalArgumentException(e);
      }
      return new URLClassLoader(jars.toArray(new URL[jars.size()]), parent);
   }

   private static void addJars(File libFolder, List<URL> jars) throws MalformedURLException {
      if (!libFolder.isDirectory()) {
         log.info("Could not find lib directory: " + libFolder.getAbsolutePath());
      } else {
         String[] jarsSrt = libFolder.list(new JarFilenameFilter());
         for (String file : jarsSrt) {
            File aJar = new File(libFolder, file);
            if (!aJar.exists() || !aJar.isFile()) {
               throw new IllegalStateException();
            }
            jars.add(aJar.toURI().toURL());
         }
      }
   }

   public static void threadDump() {
      long start = System.nanoTime();
      Map<Thread, StackTraceElement[]> stacktraces = Thread.getAllStackTraces();
      long duration = System.nanoTime() - start;
      log.warn("Thread dump took " + TimeUnit.NANOSECONDS.toMillis(duration) + " ms:");
      for (Entry<Thread, StackTraceElement[]> st : stacktraces.entrySet()) {
         StringBuilder sb = new StringBuilder();
         sb.append("Stack for thread ");
         sb.append(st.getKey().getName());
         sb.append(" (");
         sb.append(st.getKey().getState());
         sb.append("):\n");
         for (StackTraceElement ste : st.getValue()) {
            sb.append(ste.toString());
            sb.append('\n');
         }
         log.warn(sb.toString());
      }
   }

   public static byte[] readAsBytes(InputStream is) throws IOException {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      int nRead;
      byte[] data = new byte[16384];
      while ((nRead = is.read(data, 0, data.length)) != -1) {
         buffer.write(data, 0, nRead);
      }
      buffer.flush();
      return buffer.toByteArray();
   }

   public static String sanitizePath(String pluginPath) {
      if (pluginPath.startsWith("~/")) {
         pluginPath = System.getProperty("user.home") + File.separator + pluginPath.substring(2);
      }
      return pluginPath;
   }

   public static String concat(String separator, String... parts) {
      StringBuilder sb = new StringBuilder();
      for (String part : parts) {
         if (part == null || part.isEmpty()) continue;
         if (sb.length() != 0) sb.append(separator);
         sb.append(part);
      }
      return sb.toString();
   }

   public static void shutdownAndWait(ExecutorService executor) {
      executor.shutdownNow();
      try {
         if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            log.error("Failed to terminate executor.");
         }
      } catch (InterruptedException e) {
         log.error("Interrupted when waiting for the executor to finish.");
      }
   }

   public static class JarFilenameFilter implements FilenameFilter {
      public boolean accept(File dir, String name) {
         String fileName = name.toUpperCase(Locale.ENGLISH);
         if (fileName.endsWith("JAR") || fileName.endsWith("ZIP")) {
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
   }

   public static Properties getPluginProperties(String plugin) {
      ArgsHolder.PluginParam pluginParam = ArgsHolder.getPluginParams().get(plugin);
      Properties properties = new Properties();
      File pluginDir = pluginParam != null && pluginParam.getPath() != null
            ? new File(pluginParam.getPath()) : new File(Directories.PLUGINS_DIR, plugin);
      File confDir = new File(pluginDir, "conf");
      File file = new File(confDir, PROPERTIES_FILE);
      if (!file.exists()) {
         log.warn("Could not find a plugin descriptor : " + file);
         return properties;
      }
      FileInputStream inStream = null;
      try {
         inStream = new FileInputStream(file);
         properties.load(inStream);
         return properties;
      } catch (IOException e) {
         throw new RuntimeException(e);
      } finally {
         close(inStream);
      }
   }

   public static String getPluginProperty(String plugin, String propertyName) {
      return getPluginProperties(plugin).getProperty(propertyName);
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
      NumberFormat nf = NumberFormat.getNumberInstance();
      nf.setMaximumFractionDigits(2);
      if (unit.toNanos(time) < 1000) return nf.format(convert(time, unit, TimeUnit.NANOSECONDS)) + " ns";
      if (unit.toMicros(time) < 1000) return nf.format(convert(time, unit, TimeUnit.MICROSECONDS)) + " us";
      if (unit.toMillis(time) < 1000) return nf.format(convert(time, unit, TimeUnit.MILLISECONDS)) + " ms";
      if (unit.toSeconds(time) < 300) return nf.format(convert(time, unit, TimeUnit.SECONDS)) + " seconds";
      if (unit.toMinutes(time) < 120) return nf.format(convert(time, unit, TimeUnit.MINUTES)) + " minutes";
      return nf.format(convert(time, unit, TimeUnit.HOURS)) + " hours";
   }

   private static double convert(long time, TimeUnit unitIn, TimeUnit unitOut) {
      return (double) unitIn.toNanos(time) / (double) unitOut.toNanos(1);
   }

   /**
    * Prints a time for display
    *
    * @param millis time in millis
    * @return the time, represented as millis, seconds, minutes or hours as appropriate, with suffix
    */
   public static String prettyPrintMillis(long millis) {
      return prettyPrintTime(millis, TimeUnit.MILLISECONDS);
   }

   public static void sleep(long duration) {
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

   public static <T> T instantiate(ClassLoader classLoader, String className) {
      try {
         return (T) classLoader.loadClass(className).newInstance();
      } catch (Exception e) {
         throw new IllegalArgumentException(e);
      }
   }

   public static <T> T instantiateAndInit(ClassLoader classLoader, String className, String params) {
      T instance = instantiate(classLoader, className);
      if (params != null) {
         PropertyHelper.setProperties(instance, parseParams(params), false, false);
      }
      InitHelper.init(instance);
      return instance;
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

   @Deprecated
   public static void createOutputFile(String fileName, String fileContent) throws IOException {
      createOutputFile(fileName, fileContent, true);
   }

   @Deprecated
   public static void createOutputFile(String fileName, String fileContent, boolean doBackup) throws IOException {
      File parentDir = new File("reports");
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
   public static <T> T invokeMethodWithString(T object, Map<String, String> properties) {
      Class<?> clazz = object.getClass();
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

   private static volatile Field randomSeedField = null;

   public static long getRandomSeed(Random random) {
      try {
         Field field = randomSeedField;
         if (field == null) {
            field = Random.class.getDeclaredField("seed");
            field.setAccessible(true);
            randomSeedField = field;
         }
         return ((AtomicLong) field.get(random)).get();
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

   public static List<String> readFile(String file) {
      List<String> lines = new ArrayList<String>();
      BufferedReader br = null;

      InputStream resourceStream = Utils.class.getResourceAsStream(file);
      try {
         if (resourceStream != null) {
            br = new BufferedReader(new InputStreamReader(resourceStream));
         } else {
            br = new BufferedReader(new FileReader(file));
         }
         String line;
         while ((line = br.readLine()) != null) {
            lines.add(line);
         }
      } catch(IOException ex) {
         log.error("Error is thrown during file reading!", ex);
         throw new RuntimeException(ex);
      } finally {
         close(br, resourceStream);
      }
      return lines;
   }

   public static void close(Closeable... closeables) {
      IOException exception = null;
      for (Closeable closeable : closeables) {
         try {
            if (closeable != null) closeable.close();
         } catch (IOException ex) {
            log.error("Error during closing!", ex);
            if (exception == null) exception = ex;
         }
      }
      if (exception != null) {
         throw new RuntimeException(exception);
      }
   }


   public static void setField(Class<?> clazz, String fieldName, Object instance, Object value) {
      try {
         Field field = clazz.getDeclaredField(fieldName);
         field.setAccessible(true);
         if (Modifier.isFinal(field.getModifiers())) {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
         }
         field.set(instance, value);
      } catch (Exception e) {
         log.error("Failed to set " + clazz.getName() + "." + fieldName + " to value", e);
      }
   }

   public static <T> T getArg(Object[] args, int arg, Class<T> clazz) {
      if (args == null || args.length <= arg || !clazz.isInstance(args[arg])) throw new IllegalArgumentException(Arrays.toString(args));
      return (T) args[arg];
   }
}
