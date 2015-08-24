package org.radargun;

import java.io.File;

import org.radargun.config.ConfigParser;
import org.radargun.config.MasterConfig;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.ArgsHolder;

/**
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class LaunchMaster {

   private static Log log = LogFactory.getLog(LaunchMaster.class);

   public static void main(String[] args) {

      File currentDir = new File(".");
      String message = "Running in directory: " + currentDir.getAbsolutePath();
      out(message);

      String config = getConfigOrExit(args);

      out("Configuration file is: " + config);

      try {
         ConfigParser configParser = ConfigParser.getConfigParser();
         MasterConfig masterConfig = configParser.parseConfig(config);
         Master master = new Master(masterConfig);
         master.run();
      } catch (Exception e) {
         log.error("Master failed", e);
         e.printStackTrace();
         ShutDownHook.exit(127);
      }
   }

   public static String getConfigOrExit(String[] args) {
      ArgsHolder.init(args, ArgsHolder.ArgType.LAUNCH_MASTER);
      if (ArgsHolder.getConfigFile() == null) {
         ArgsHolder.printUsageAndExit(ArgsHolder.ArgType.LAUNCH_MASTER);
      }
      return ArgsHolder.getConfigFile();
   }

   private static void out(String message) {
      System.out.println(message);
      log.info(message);
   }
}
