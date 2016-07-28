package org.radargun.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.radargun.Directories;
import org.radargun.Slave;
import org.radargun.config.Evaluator;
import org.radargun.config.VmArgs;
import org.radargun.logging.LogFactory;

/**
 * This utility will start another process after certain lock (args[0]) is released.
 */
public final class RestartHelper {


   private RestartHelper() {}

   public static void init() {
      if (ArgsHolder.getTempConfigDir() != null) {
         Utils.deleteOnExitRecursive(new File(ArgsHolder.getTempConfigDir()));
      }
   }

   public static void spawnSlave(int slaveIndex, UUID nextUuid, String plugin, VmArgs vmArgs, HashMap<String, String> envs) throws IOException {
      ProcessBuilder processBuilder = new ProcessBuilder();
      processBuilder.inheritIO();
      StringBuilder classpathBuilder = new StringBuilder();
      // plugin-specific stuff should be prepended
      ArgsHolder.PluginParam pluginParam = ArgsHolder.getPluginParams().get(plugin);
      Path tempConfigDir = null;
      if (pluginParam != null) {
         if (pluginParam.getPath() != null) {
            File extDir = new File(pluginParam.getPath());
            if (extDir.exists() && extDir.isDirectory()) {
               addConfAndLib(classpathBuilder, extDir);
            }
         }
         if (!pluginParam.getConfigFiles().isEmpty()) {
            tempConfigDir = Files.createTempDirectory("radargun-" + plugin + "-" + slaveIndex);
            classpathBuilder.append(File.pathSeparatorChar).append(tempConfigDir.toAbsolutePath());
            for (String configFile : pluginParam.getConfigFiles()) {
               File file = new File(configFile);
               Files.copy(file.toPath(), tempConfigDir.resolve(file.getName()));
            }
         }
      }
      File pluginDir = new File(Directories.PLUGINS_DIR, plugin);
      if (pluginDir.exists() && pluginDir.isDirectory()) {
         addConfAndLib(classpathBuilder, pluginDir);
      }
      // if plugin requires something specific on classpath, retrieve that
      String extraClassPath = Utils.getPluginProperty(plugin, "classpath");
      classpathBuilder.append(File.pathSeparatorChar).append(Evaluator.parseString(extraClassPath));
      addConfAndLib(classpathBuilder, Directories.ROOT_DIR);

      ListBuilder<String> command = new ListBuilder<>(new ArrayList<String>());
      String javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
      // we need to run intermediate process that waits until this process ends
      command.add(javaExecutable)
         .add("-cp").add(Directories.LIB_DIR.toString() + "/*").add(RestartHelper.class.getName())
         .add(createLockedTempFile(slaveIndex));
      command.add(javaExecutable);
      List<String> defaultVmArgs = ArgsHolder.getDefaultVmArgs();
      command.addAll(vmArgs.getVmArgs(defaultVmArgs));
      // we have to specify log4j configuration explicitly because plugin can define its default config
      if (!defaultVmArgs.stream().anyMatch(s -> s.startsWith("-Dlog4j.configuration"))) {
         command.add("-Dlog4j.configuration=file://" + Directories.ROOT_DIR + "/conf/log4j.xml");
      }
      if (!defaultVmArgs.stream().anyMatch(s -> s.startsWith("-Dlog4j.configurationFile"))) {
         command.add("-Dlog4j.configurationFile=file://" + Directories.ROOT_DIR + "/conf/log4j2.xml");
      }
      command.add("-cp").add(classpathBuilder.toString());
      command.add(Slave.class.getName());
      command.add(ArgsHolder.MASTER).add(ArgsHolder.getMasterHost() + ":" + ArgsHolder.getMasterPort());
      command.add(ArgsHolder.SLAVE_INDEX).add(String.valueOf(slaveIndex));
      command.add(ArgsHolder.UUID).add(nextUuid.toString());
      command.add(ArgsHolder.CURRENT_PLUGIN).add(plugin);
      if (tempConfigDir != null) {
         command.add(ArgsHolder.TEMP_CONFIG_DIR).add(tempConfigDir.toString());
      }
      // we have to repeat the args for future generations
      for (Map.Entry<String, ArgsHolder.PluginParam> entry : ArgsHolder.getPluginParams().entrySet()) {
         ArgsHolder.PluginParam pp = entry.getValue();
         if (pp.getPath() != null) {
            command.add(ArgsHolder.ADD_PLUGIN).add(pp.getPath());
         }
         for (String configFile : pp.getConfigFiles()) {
            command.add(ArgsHolder.ADD_CONFIG).add(entry.getKey() + ":" + configFile);
         }
      }
      for (String vmArg : defaultVmArgs) {
         command.add(ArgsHolder.DEFAULT_VM_ARG).add(vmArg);
      }

      LogFactory.getLog(RestartHelper.class).info("VM start command = " + command.build().toString());
      processBuilder.command(command.build());
      processBuilder.environment().putAll(envs);
      processBuilder.start();
   }

   private static String createLockedTempFile(int slaveIndex) throws IOException {
      File tempFile = File.createTempFile("restart-" + slaveIndex, ".tmp");
      new RandomAccessFile(tempFile, "rw").getChannel().lock();
      return tempFile.getAbsolutePath();
   }

   private static void addConfAndLib(StringBuilder classpathBuilder, File parentDir) {
      if (classpathBuilder.length() > 0) {
         classpathBuilder.append(File.pathSeparatorChar);
      }
      classpathBuilder.append(parentDir).append(File.separatorChar).append("conf/");
      for (File f : new File(parentDir, "lib").listFiles()) {
         classpathBuilder.append(File.pathSeparatorChar);
         classpathBuilder.append(f.getAbsolutePath());
      }
   }

   public static void main(String[] args) throws IOException {
      File file = new File(args[0]);
      System.out.printf("%s: Waiting for lock on file %s%n", timestamp(), args[0]);
      FileLock lock = new RandomAccessFile(file, "rw").getChannel().lock();
      System.out.printf("%s: Lock acquired%n", timestamp());
      lock.release();
      file.delete();

      ProcessBuilder processBuilder = new ProcessBuilder();
      processBuilder.inheritIO();
      ArrayList<String> command = new ArrayList<>();
      for (int i = 1; i < args.length; ++i) command.add(args[i]);
      processBuilder.command(command);
      processBuilder.start();
      System.exit(0);
   }

   private static String timestamp() {
      return new SimpleDateFormat("HH:mm:ss,S").format(new Date());
   }
}