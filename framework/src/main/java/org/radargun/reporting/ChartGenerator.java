package org.radargun.reporting;

import org.radargun.stages.GenerateChartStage;

import java.io.IOException;
import java.util.StringTokenizer;

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
      System.out.println("   ChartGenerator [-reportDir <directory containing CSV files>] [-o <outputFileNamePrefix>] [-filter <productName>(config1,confi2);<productName2>(config1,config2,config3)] ]");
   }

   public static void main(String[] args) throws Exception {
      String reportDirectory = null;


      String fnPrefix = null;
      String filter = null;

      long startTime = System.currentTimeMillis();
      System.out.println("Welcome to the ChartGenerator.");
      // the params we expect:
      for (int i = 0; i < args.length; i++) {
         if (args[i].equals("-reportDir")) {
            reportDirectory = args[++i];
            continue;
         }

         if (args[i].equals("-o")) {
            fnPrefix = args[++i];
            continue;
         }

         if (args[i].equals("-filter")) {
            filter = args[++i];
            continue;
         }

         help();
         return;
      }

      if (reportDirectory == null) {
         help();
         return;
      }


      GenerateChartStage generateChartStage = new GenerateChartStage();
      generateChartStage.setCsvFilesDirectory(reportDirectory);
      generateChartStage.setFnPrefix(fnPrefix);
      generateChartStage.setReportDirectory(reportDirectory);

      if (filter != null) {
         StringTokenizer products = new StringTokenizer(filter, ";");
         while (products.hasMoreTokens()) {
            String product = products.nextToken();
            int configsStart = product.indexOf('(');
            String productName = product.substring(0, configsStart);
            String productConfigs = product.substring(configsStart + 1, product.length() - 1);
            StringTokenizer configTokenizer = new StringTokenizer(productConfigs, ",");
            while (configTokenizer.hasMoreTokens()) {
               String config = configTokenizer.nextToken();
               generateChartStage.addReportFilter(productName, config);
            }
         }
      }
      generateChartStage.execute();
      System.out.println("Finished in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds!");
   }
}
