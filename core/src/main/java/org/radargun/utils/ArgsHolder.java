package org.radargun.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.radargun.RemoteSlaveConnection;
import org.radargun.ShutDownHook;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * <p>Holder for input arguments of {@link org.radargun.LaunchMaster} and {@link org.radargun.Slave}.</p>
 *
 * <p>Supported arguments - {@link org.radargun.Slave}:
 * <ul><li>Master host (required) & port (optional, defaults to 2103): -master 127.0.0.1:2101</li>
 * <li>Slave index (optional): -slaveIndex 1</li></ul></p>
 *
 * <p>Supported arguments - {@link org.radargun.LaunchMaster}:
 * <ul><li>Benchmark's config file (required): -config /path/to/config.xml</li>
 * <li>Location of reporter's dir (optional): --add-reporter=/path/to/custom-reporter</li></ul></p>
 *
 * <p>Supported arguments - applicable for {@link org.radargun.Slave} (clustered mode)
 * and {@link org.radargun.LaunchMaster} (local mode):
 * <ul><li>Location of plugin dir (containing conf and lib folders, optional): --add-plugin=/path/to/custom-plugin</li>
 * <li>Location of configuration file (optional): --add-config=custom-plugin:/path/to/custom-plugin.xml</li></ul></p>
 *
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class ArgsHolder {
   protected static final String CONFIG = "--config";
   protected static final String ADD_REPORTER = "--add-reporter";
   protected static final String ADD_PLUGIN = "--add-plugin";
   protected static final String ADD_CONFIG = "--add-config";
   private static Log log = LogFactory.getLog(ArgsHolder.class);

   protected static final String TEMP_CONFIG_DIR = "--temp-config-dir";
   protected static final String UUID = "--uuid";
   protected static final String SLAVE_INDEX = "--slaveIndex";
   protected static final String MASTER = "--master";

   private static String configFile;
   private static String masterHost;
   private static int masterPort = RemoteSlaveConnection.DEFAULT_PORT;
   private static int slaveIndex = -1;
   private static UUID uuid;
   private static String tempConfigDir = null;

   private static Map<String, PluginParam> pluginParams = new HashMap<String, PluginParam>();
   private static List<String> reporterPaths = new ArrayList<String>();

   private ArgsHolder() {
   }

   /**
    * Initializes ArgsHolder
    *
    * @param args input arguments
    * @param type type of argument (LAUNCH_MASTER, SLAVE)
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
         if (ArgType.SLAVE == type) {
            switch (arg) {
               case "-master":
                  log.warn("Switch -master is deprecated. Use --master instead.");
               case MASTER:
                  param = nextArg(arg, argList);
                  if (param.contains(":")) {
                     masterHost = param.substring(0, param.indexOf(":"));
                     try {
                        masterPort = Integer.parseInt(param.substring(param.indexOf(":") + 1));
                     } catch (NumberFormatException nfe) {
                        log.error("Unable to parse port part of the master! Failing!");
                        ShutDownHook.exit(127);
                     }
                  } else {
                     masterHost = param;
                  }
                  break;
               case "-slaveIndex":
                  log.warn("Switch -slaveIndex is deprecated. Use --slaveIndex instead.");
               case SLAVE_INDEX:
                  param = nextArg(arg, argList);
                  try {
                     slaveIndex = Integer.parseInt(param);
                  } catch (NumberFormatException nfe) {
                     log.errorf("Unable to parse slaveIndex!  Failing!");
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
               default:
                  processCommonArgs(arg, argList, type);
            }
         }
         if (ArgType.LAUNCH_MASTER == type) {
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
      if (ArgType.SLAVE == type) {
         printSlaveUsageAndExit();
      } else if (ArgType.LAUNCH_MASTER == type) {
         printMasterUsageAndExit();
      }
   }

   private static void printMasterUsageAndExit() {
      System.out.println("Usage: master.sh  --config <config-file.xml>");
      System.out.println("       --config : xml file containing benchmark's configuration");
      ShutDownHook.exit(127);
   }

   private static void printSlaveUsageAndExit() {
      System.out.println("Usage: slave.sh --master <host>:port");
      System.out.println("       --master: The host(and optional port) on which the master resides. If port is missing it defaults to " + RemoteSlaveConnection.DEFAULT_PORT);
      ShutDownHook.exit(127);
   }

   public static enum ArgType {
      LAUNCH_MASTER, SLAVE
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

   public static String getMasterHost() {
      return masterHost;
   }

   public static int getMasterPort() {
      return masterPort;
   }

   public static int getSlaveIndex() {
      return slaveIndex;
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
}
