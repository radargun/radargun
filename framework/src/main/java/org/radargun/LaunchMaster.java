package org.radargun;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.config.ConfigParser;
import org.radargun.config.MasterConfig;

import java.io.File;

/**
 * @author Mircea.Markus@jboss.com
 */
public class LaunchMaster {

   private static Log log = LogFactory.getLog(LaunchMaster.class);

   public static void main(String[] args) throws Exception {

      File currentDir = new File(".");
      String message = "Running in directory: " + currentDir.getAbsolutePath();
      out(message);

      String config = getConfigOrExit(args);

      out("Configuration file is: " + config);

      ConfigParser configParser = ConfigParser.getConfigParser();
      MasterConfig masterConfig = configParser.parseConfig(config);
      Master server = new Master(masterConfig);
      server.start();
   }

   private static String getConfigOrExit(String[] args) {
      String config = null;
      for (int i = 0; i < args.length - 1; i++) {
         if (args[i].equals("-config")) {
            config = args[i + 1];
         }
      }
      if (config == null) {
         printUsageAndExit();
      }
      return config;
   }

   private static void printUsageAndExit() {
      System.out.println("Usage: master.sh  -config <config-file.xml>");
      System.out.println("       -config : xml file containing benchmark's configuration");
      ShutDownHook.exit(1);
   }


   private static void launchOld(String config) throws Exception {
      File configFile = new File(config);
      if (!configFile.exists()) {
         System.err.println("No such file: " + configFile.getAbsolutePath());
         printUsageAndExit();
      }
   }

   private static void out(String message) {
      System.out.println(message);
      log.info(message);
   }
}
