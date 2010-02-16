package org.cachebench.local;

import org.cachebench.ShutDownHook;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class LaunchLocal {
   public static void main(String[] args) throws Exception {
      String config = getConfigOrExit(args);
      LocalConfigParser parser = new LocalConfigParser();
      LocalBenchmark benchmark = parser.parse(config);
      benchmark.benchmark();
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
      System.out.println("Usage: local.sh  -config <config-file.xml>");
      System.out.println("       -config : xml file containing local benchmark's configuration");
      ShutDownHook.exit(1);
   }

}
