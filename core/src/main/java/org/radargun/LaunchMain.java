package org.radargun;

import java.io.File;

import org.radargun.config.ConfigParser;
import org.radargun.config.MainConfig;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.ArgsHolder;

/**
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public final class LaunchMain {

   private static Log log = LogFactory.getLog(LaunchMain.class);

   private LaunchMain() {}

   public static void main(String[] args) {

      File currentDir = new File(".");
      String message = "Running in directory: " + currentDir.getAbsolutePath();
      out(message);

      String config = getConfigOrExit(args);

      out("Configuration file is: " + config);

      try {
         ConfigParser configParser = ConfigParser.getConfigParser();
         MainConfig mainConfig = configParser.parseConfig(config);
         mainConfig.applyTemplates();
         Main main = new Main(mainConfig);
         main.run();
      } catch (Exception e) {
         log.error("Main failed", e);
         e.printStackTrace();
         ShutDownHook.exit(127);
      }
   }

   public static String getConfigOrExit(String[] args) {
      ArgsHolder.init(args, ArgsHolder.ArgType.LAUNCH_MAIN);
      if (ArgsHolder.getConfigFile() == null) {
         ArgsHolder.printUsageAndExit(ArgsHolder.ArgType.LAUNCH_MAIN);
      }
      return ArgsHolder.getConfigFile();
   }

   private static void out(String message) {
      System.out.println(message);
      log.info(message);
   }
}
