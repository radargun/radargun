package org.cachebench.reporting;

import java.io.IOException;

/**
 * Manual chart generator.  Grabs CSVs generated and spits out line graphs.
 * <p/>
 * This generator draws line graphs, using cluster size on the X axis (e.g., 2, 4, 6 nodes) and throughput (requests per
 * second) on the Y axis.  It then plots points for the different cache products and configurations tested.
 * <p/>
 * For the time being, this is expected to be run manually on the command line, passing in relevant parameters.  Calling
 * this with no params will generate some help.
 * <p/>
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 */
public class ChartGenerator {
   private static void help() {
      System.out.println("Usage:");
      System.out.println("   ChartGenerator [-reportDir <directory containing CSV files>] [-o <outputFileNamePrefix>] [-singleChart <true | false> if true, generates a single chart for all config files.] [-chartType <putget | throughput> defaults to throughput if not specified.]");
   }

   public static void main(String[] args) throws IOException {
      String reportDirectory = null;
      boolean singleChart = true;
      String fnPrefix = null;
      String chartType = "throughput";

      long startTime = System.currentTimeMillis();
      System.out.println("Welcome to the ChartGenerator.");
      // the params we expect:
      for (int i = 0; i < args.length; i++) {
         if (args[i].equals("-reportDir")) {
            reportDirectory = args[++i];
            continue;
         }

         if (args[i].equals("-singleChart")) {
            singleChart = Boolean.valueOf(args[++i]);
            continue;
         }

         if (args[i].equals("-o")) {
            fnPrefix = args[++i];
            continue;
         }

         if (args[i].equals("-chartType")) {
            chartType = args[++i];
            continue;
         }

         help();
         return;
      }

      if (reportDirectory == null) {
         help();
         return;
      }
      if (!singleChart) throw new RuntimeException("Multiple charts not yet implemented");

      ChartGen gen;

      if (chartType.equalsIgnoreCase("putget")) {
         gen = new PutGetChartGenerator();
      } else {
         gen = new ThroughputChartGenerator();
      }

      gen.setReportDirectory(reportDirectory);
      gen.setFileNamePrefix(fnPrefix);
      gen.generateChart();
      System.out.println("Finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds!");
   }
}
