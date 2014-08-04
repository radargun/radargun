package org.radargun.utils;

import org.radargun.RemoteSlaveConnection;
import org.radargun.ShutDownHook;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

import java.util.*;

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

   private static Log log = LogFactory.getLog(ArgsHolder.class);

   private static String configFile;
   private static String masterHost;
   private static int masterPort = RemoteSlaveConnection.DEFAULT_PORT;
   private static int slaveIndex = -1;

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
      ArgsParser.parseArgs(args, type);
   }

   /**
    * Parser of input arguments
    */
   private static class ArgsParser {

      public static void parseArgs(String[] args, ArgType type) {
         for (int i = 0; i < args.length; i++) {
            if (ArgType.SLAVE == type) {
               if (args[i].equals("-master") && i < args.length - 1) {
                  String param = args[i + 1];
                  if (param.contains(":")) {
                     masterHost = param.substring(0, param.indexOf(":"));
                     try {
                        masterPort = Integer.parseInt(param.substring(param.indexOf(":") + 1));
                     } catch (NumberFormatException nfe) {
                        log.warn("Unable to parse port part of the master!  Failing!");
                        ShutDownHook.exit(10);
                     }
                  } else {
                     masterHost = param;
                  }
               } else if (args[i].equals("-slaveIndex") && i < args.length - 1) {
                  try {
                     slaveIndex = Integer.parseInt(args[i + 1]);
                  } catch (NumberFormatException nfe) {
                     log.warn("Unable to parse slaveIndex!  Failing!");
                     ShutDownHook.exit(10);
                  }
               }
            }
            if (ArgType.LAUNCH_MASTER == type) {
               if (args[i].equals("-config") && i < args.length - 1) {
                  configFile = args[i + 1];
               } else if (args[i].startsWith("--add-reporter")) {
                  String reporterPath = args[i].split("=")[1];
                  reporterPaths.add(reporterPath);
               }
            }
            if (args[i].matches("--add-plugin=.+")) {
               String pluginPath = args[i].split("=")[1];
               String pluginName = pluginPath.substring(pluginPath.lastIndexOf("/") + 1, pluginPath.length());
               PluginParam pluginParam = pluginParams.get(pluginName);
               if (pluginParam == null) {
                  pluginParams.put(pluginName, new PluginParam(pluginPath, null));
               } else {
                  pluginParam.setPath(pluginPath);
               }
            } else if (args[i].matches("--add-config=.+:.+")) {
               String configPath = args[i].split("=")[1];
               String pluginName = configPath.substring(0, configPath.indexOf(":"));
               String configFile = configPath.substring(configPath.indexOf(":") + 1, configPath.length());
               PluginParam pluginParam = pluginParams.get(pluginName);
               if (pluginParam == null) {
                  pluginParams.put(pluginName, new PluginParam(null, Arrays.asList(configFile)));
               } else {
                  if (pluginParam.getConfigFiles() == null) {
                     pluginParam.setConfigFiles(new ArrayList<String>());
                  }
                  pluginParam.getConfigFiles().add(configFile);
               }
            }
         }
      }
   }

   public static enum ArgType {
      LAUNCH_MASTER, SLAVE
   }

   public static class PluginParam {
      private String path;
      private List<String> configFiles;

      public PluginParam(String path, List<String> configFiles) {
         this.path = path;
         this.configFiles = configFiles;
      }

      public String getPath() {
         return path;
      }

      public void setPath(String path) {
         this.path = path;
      }

      public List<String> getConfigFiles() {
         return configFiles;
      }

      public void setConfigFiles(List<String> configFiles) {
         this.configFiles = configFiles;
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

   public static Map<String, PluginParam> getPluginParams() {
      return Collections.unmodifiableMap(pluginParams);
   }

   public static List<String> getReporterPaths() {
      return Collections.unmodifiableList(reporterPaths);
   }
}
