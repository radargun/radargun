package org.radargun.reporting.serialized;

import java.io.File;
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

import org.radargun.config.DomConfigParser;
import org.radargun.config.MasterConfig;
import org.radargun.config.Property;
import org.radargun.config.ReporterConfiguration;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.Reporter;
import org.radargun.reporting.ReporterHelper;
import org.radargun.utils.Utils;

/**
 * Serializes all data from the report to disc, in order to create reports in the future.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SerializedReporter implements Reporter {
   private static final Log log = LogFactory.getLog(SerializedReporter.class);

   @Property(doc = "Directory where the results should be stored. Default is results/serialized.")
   protected String targetDir = "results" + File.separator + "serialized";

   @Override
   public void run(Collection<Report> reports) {
      File dir = new File(targetDir);
      if (!dir.exists()) {
         dir.mkdirs();
      }
      DateFormat formatter = new SimpleDateFormat("yyyyMMdd-HHmmss");
      for (Report report : reports) {
         String filename = String.format("%s-%s-%s-%s.bin", report.getConfiguration().name,
               report.getCluster().getSize(), report.getCluster().getClusterIndex(), formatter.format(new Date()));
         FileOutputStream fileOutputStream = null;
         ObjectOutputStream objectOutputStream = null;
         try {
            fileOutputStream = new FileOutputStream(new File(dir, filename));
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(report);
         } catch (IOException e) {
            log.error("Failed to write report to " + filename, e);
         } finally {
            Utils.close(fileOutputStream, objectOutputStream);
         }
      }
   }

   public static void main(String args[]) {
      if (args.length < 2) {
         System.err.println("java " + SerializedReporter.class.getName() + " benchmark.xml /path/to/target-dir [reporter-dir...]");
         return;
      }
      String benchmark = args[0];
      String targetDir = args[1];
      for (int i = 2; i < args.length; ++i) {
         ReporterHelper.registerReporters(args[i]);
      }

      MasterConfig config;
      try {
         config = DomConfigParser.getConfigParser().parseConfig(benchmark);
      } catch (Exception e) {
         System.err.println("Failed to parse " + benchmark);
         e.printStackTrace();
         return;
      }

      List<Report> reports = new ArrayList<>();
      for (File reportFile : new File(targetDir).listFiles()) {
         FileInputStream fileInputStream = null;
         ObjectInputStream objectInputStream = null;
         try {
            fileInputStream = new FileInputStream(reportFile);
            objectInputStream = new ObjectInputStream(fileInputStream);
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
         } finally {
            Utils.close(fileInputStream, objectInputStream);
         }
      }

      for (ReporterConfiguration rc : config.getReporters()) {
         for (ReporterConfiguration.Report rcr : rc.getReports()) {
            try {
               Reporter reporter = ReporterHelper.createReporter(rc.type, rcr.getProperties());
               if (reporter instanceof SerializedReporter) continue;
               reporter.run(reports);
            } catch (Exception e) {
               System.err.println("Failed to run reporter " + rc.type);
               e.printStackTrace();
            }
         }
      }
   }
}
