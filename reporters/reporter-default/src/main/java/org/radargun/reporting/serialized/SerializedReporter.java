package org.radargun.reporting.serialized;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.radargun.ShutDownHook;
import org.radargun.config.DomConfigParser;
import org.radargun.config.InitHelper;
import org.radargun.config.MasterConfig;
import org.radargun.config.Property;
import org.radargun.config.ReporterConfiguration;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.Reporter;
import org.radargun.reporting.ReporterHelper;

/**
 * Serializes all data from the report to disc, in order to create reports in the future.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SerializedReporter implements Reporter {
   private static final Log log = LogFactory.getLog(SerializedReporter.class);
   private static final String FILE_EXTENSION = ".bin";

   @Property(doc = "Directory where the results should be stored. Default is results/serialized.")
   protected String targetDir = "results" + File.separator + "serialized";

   @Override
   public void run(Collection<Report> reports) {
      File dir = new File(targetDir);
      dir.mkdirs();
      DateFormat formatter = new SimpleDateFormat("yyyyMMdd-HHmmss");
      for (Report report : reports) {
         String filename = String.format("%s-%s-%s-%s%s", report.getConfiguration().name,
                                         report.getCluster().getSize(), report.getCluster().getClusterIndex(),
                                         formatter.format(new Date()), FILE_EXTENSION);
         try (FileOutputStream fileOutputStream = new FileOutputStream(new File(dir, filename));
              ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
            objectOutputStream.writeObject(report);
         } catch (IOException e) {
            log.error("Failed to write report to " + filename, e);
         }
      }
   }

   public static void main(String args[]) {
      if (args.length < 2) {
         System.err.println("java " + SerializedReporter.class.getName() + " benchmark.xml [--add-result-dir=/path/to/target-dir]+ [--add-reporter-dir=/path/to/reporter-dir]*");
         return;
      }

      List<Report> reports = new ArrayList<>();
      for (int i = 1; i < args.length; ++i) {
         String arg = args[i];
         if (arg.matches("--add-result-dir=.+")) {
            addReport(reports, arg.substring(arg.indexOf("=") + 1));
         } else if (arg.matches("--add-reporter-dir=.+")) {
            ReporterHelper.registerReporters(arg.substring(arg.indexOf("=") + 1));
         } else {
            throw new IllegalArgumentException("Parameter " + arg + " not recognized");
         }
      }
      if (reports.isEmpty()) {
         throw new IllegalArgumentException("No reports have been initialized. Check whether correct result directory paths were provided");
      }

      String benchmark = args[0];
      MasterConfig config;
      try {
         config = DomConfigParser.getConfigParser().parseConfig(benchmark);
      } catch (Exception e) {
         System.err.println("Failed to parse " + benchmark);
         e.printStackTrace();
         return;
      }

      for (ReporterConfiguration rc : config.getReporters()) {
         for (ReporterConfiguration.Report rcr : rc.getReports()) {
            Reporter reporter = null;
            try {
               reporter = ReporterHelper.createReporter(rc.type, rcr.getProperties());
               if (reporter instanceof SerializedReporter) continue;
               reporter.run(reports);
            } catch (Exception e) {
               System.err.println("Failed to run reporter " + rc.type);
               e.printStackTrace();
            } finally {
               if (reporter != null) {
                  InitHelper.destroy(reporter);
               }
            }
         }
      }

      ShutDownHook.exit(0); // the shutdown is controlled
   }

   private static void addReport(List<Report> reports, String resultDir) {
      for (File reportFile : new File(resultDir).listFiles(new FileFilter() {
         @Override
         public boolean accept(File pathname) {
            return pathname.getName().toLowerCase().endsWith(FILE_EXTENSION) && !pathname.isDirectory();
         }
      })) {
         try (FileInputStream fileInputStream = new FileInputStream(reportFile); ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            Object obj = objectInputStream.readObject();
            if (obj instanceof Report) {
               reports.add((Report) obj);
            } else {
               System.err.println(obj + " is not a report");
            }
         } catch (IOException e) {
            System.err.println("Failed to read " + reportFile);
            e.printStackTrace();
         } catch (ClassNotFoundException e) {
            System.err.println("Failed to load class from " + reportFile);
            e.printStackTrace();
         }
      }
   }
}
