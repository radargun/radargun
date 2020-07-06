package org.radargun.utils;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.radargun.RemoteWorkerConnection;
import org.radargun.ShutDownHook;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * <p>Holder for input arguments of {@link org.radargun.LaunchMain} and {@link org.radargun.Worker}.</p>
 *
 * <p>Supported arguments - {@link org.radargun.Worker}:
 * <ul><li>Main host (required) & port (optional, defaults to 2103): -main 127.0.0.1:2101</li>
 * <li>Worker index (optional): -workerIndex 1</li></ul></p>
 *
 * <p>Supported arguments - {@link org.radargun.LaunchMain}:
 * <ul><li>Benchmark's config file (required): -config /path/to/config.xml</li>
 * <li>Location of reporter's dir (optional): --add-reporter=/path/to/custom-reporter</li></ul></p>
 *
 * <p>Supported arguments - applicable for {@link org.radargun.Worker} (clustered mode)
 * and {@link org.radargun.LaunchMain} (local mode):
 * <ul><li>Location of plugin dir (containing conf and lib folders, optional): --add-plugin=/path/to/custom-plugin</li>
 * <li>Location of configuration file (optional): --add-config=custom-plugin:/path/to/custom-plugin.xml</li></ul></p>
 *
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class ArgsHolder {
   private static Log log = LogFactory.getLog(ArgsHolder.class);
   protected static final String CONFIG = "--config";
   protected static final String ADD_REPORTER = "--add-reporter";
   protected static final String ADD_PLUGIN = "--add-plugin";
   protected static final String ADD_CONFIG = "--add-config";
   protected static final String DEFAULT_VM_ARG = "--default-vm-arg";

   protected static final String TEMP_CONFIG_DIR = "--temp-config-dir";
   protected static final String UUID = "--uuid";
   protected static final String CURRENT_PLUGIN = "--current-plugin";
   protected static final String WORKER_INDEX = "--workerIndex";
   protected static final String MAIN = "--main";

   private static String configFile;
   private static String mainHost;
   private static int mainPort = RemoteWorkerConnection.DEFAULT_PORT;
   private static int workerIndex = -1;
   private static UUID uuid;
   private static String tempConfigDir;
   private static String currentPlugin;
   private static List<String> defaultVmArgs = new ArrayList<>();
   private static Map<String, PluginParam> pluginParams = new HashMap<>();
   private static List<String> reporterPaths = new ArrayList<>();

   private ArgsHolder() {
   }

   /**
    * Initializes ArgsHolder
    *
    * @param args input arguments
    * @param type type of argument (LAUNCH_MAIN, WORKER)
    */
   public static void init(String[] args, ArgType type) {
      LinkedList<String> argList = new LinkedList<>(Arrays.asList(args));
      while (!argList.isEmpty()) {
         String arg = argList.removeFirst();
         // change --foo=bar into --foo bar
         int index = arg.indexOf('=');
         if (index >= 0) {
            argList.addFirst(arg.substring(index + 1));
            arg = arg.substring(0, index);
         }
         String param;
         if (ArgType.WORKER == type) {
            switch (arg) {
               case "-main":
                  log.warn("Switch -main is deprecated. Use --main instead.");
               case MAIN:
                  param = nextArg(arg, argList);
                  if (param.contains(":")) {
                     mainHost = param.substring(0, param.indexOf(":"));
                     try {
                        mainPort = Integer.parseInt(param.substring(param.indexOf(":") + 1));
                     } catch (NumberFormatException nfe) {
                        log.error("Unable to parse port part of the main! Failing!");
                        ShutDownHook.exit(127);
                     }
                  } else {
                     mainHost = param;
                  }
                  break;
               case "-workerIndex":
                  log.warn("Switch -workerIndex is deprecated. Use --workerIndex instead.");
               case WORKER_INDEX:
                  param = nextArg(arg, argList);
                  try {
                     workerIndex = Integer.parseInt(param);
                  } catch (NumberFormatException nfe) {
                     log.errorf("Unable to parse workerIndex!  Failing!");
                     ShutDownHook.exit(127);
                  }
                  break;
               case UUID:
                  param = nextArg(arg, argList);
                  try {
                     uuid = java.util.UUID.fromString(param);
                  } catch (IllegalArgumentException nfe) {
                     log.errorf("Unable to parse UUID %s! Failing!", param);
                     ShutDownHook.exit(127);
                  }
                  break;
               case TEMP_CONFIG_DIR:
                  tempConfigDir = nextArg(arg, argList);
                  break;
               case CURRENT_PLUGIN:
                  currentPlugin = nextArg(arg, argList);
                  break;
               case DEFAULT_VM_ARG:
                  defaultVmArgs.add(nextArg(arg, argList));
                  break;
               default:
                  processCommonArgs(arg, argList, type);
            }
         }
         if (ArgType.LAUNCH_MAIN == type) {
            switch (arg) {
               case "-config":
                  log.warn("Switch -config is deprecated. Use --config instead.");
               case CONFIG:
                  configFile = nextArg(arg, argList);
                  break;
               case ADD_REPORTER:
                  reporterPaths.add(nextArg(arg, argList));
                  break;
               default:
                  processCommonArgs(arg, argList, type);
            }
         }
      }
      // when no default VM arguments are specified, we take all arguments assigned to VM
      // as default ones
      if (defaultVmArgs.isEmpty()) {
         defaultVmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
      }
   }

   private static String nextArg(String arg, LinkedList<String> argList) {
      if (argList.isEmpty()) {
         log.errorf("%s requires an argument!", arg);
         ShutDownHook.exit(127);
      }
      return argList.removeFirst();
   }

   private static void processCommonArgs(String arg, LinkedList<String> argList, ArgType type) {
      String param, pluginName;
      PluginParam pluginParam;
      switch (arg) {
         case ADD_PLUGIN:
            param = Utils.sanitizePath(nextArg(arg, argList));
            pluginName = param.substring(param.lastIndexOf("/") + 1, param.length());
            pluginParam = pluginParams.get(pluginName);
            if (pluginParam == null) {
               pluginParams.put(pluginName, pluginParam = new PluginParam());
            }
            pluginParam.setPath(param);
            break;
         case ADD_CONFIG:
            param = nextArg(arg, argList);
            pluginName = param.substring(0, param.indexOf(":"));
            String configPath = param.substring(param.indexOf(":") + 1, param.length());
            configPath = Utils.sanitizePath(configPath);
            pluginParam = pluginParams.get(pluginName);
            if (pluginParam == null) {
               pluginParams.put(pluginName, pluginParam = new PluginParam());
            }
            pluginParam.getConfigFiles().add(configPath);
            break;
         default:
            printUsageAndExit(type);
      }
   }

   public static void printUsageAndExit(ArgType type) {
      if (ArgType.WORKER == type) {
         printWorkerUsageAndExit();
      } else if (ArgType.LAUNCH_MAIN == type) {
         printMainUsageAndExit();
      }
   }

   private static void printMainUsageAndExit() {
      System.out.println("Usage: main.sh  --config <config-file.xml>");
      System.out.println("       --config : xml file containing benchmark's configuration");
      ShutDownHook.exit(127);
   }

   private static void printWorkerUsageAndExit() {
      System.out.println("Usage: worker.sh --main <host>:port");
      System.out.println("       --main: The host(and optional port) on which the main resides. If port is missing it defaults to " + RemoteWorkerConnection.DEFAULT_PORT);
      ShutDownHook.exit(127);
   }

   public static enum ArgType {
      LAUNCH_MAIN, WORKER
   }

   public static class PluginParam {
      private String path;
      private List<String> configFiles = new ArrayList<>();

      public String getPath() {
         return path;
      }

      public void setPath(String path) {
         this.path = path;
      }

      public List<String> getConfigFiles() {
         return configFiles;
      }
   }

   public static String getConfigFile() {
      return configFile;
   }

   public static String getMainHost() {
      return mainHost;
   }

   public static int getMainPort() {
      return mainPort;
   }

   public static int getWorkerIndex() {
      return workerIndex;
   }

   public static UUID getUuid() {
      return uuid;
   }

   public static Map<String, PluginParam> getPluginParams() {
      return Collections.unmodifiableMap(pluginParams);
   }

   public static List<String> getReporterPaths() {
      return Collections.unmodifiableList(reporterPaths);
   }

   public static String getTempConfigDir() {
      return tempConfigDir;
   }

   public static String getCurrentPlugin() {
      return currentPlugin;
   }

   public static void setCurrentPlugin(String currentPlugin) {
      ArgsHolder.currentPlugin = currentPlugin;
   }

   public static List<String> getDefaultVmArgs() {
      return defaultVmArgs;
   }
}
