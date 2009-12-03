package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.reporting.ChartGen;
import org.cachebench.reporting.PutGetChartGenerator;
import org.cachebench.reporting.ThroughputChartGenerator;
import org.cachebench.utils.Utils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Stage that generates a chart from a set of csv files.
 * <pre>
 * - fnPrefix: the prefix of the generated chart file (png). No value by default, optional parameter.
 * - reportDirectory - where are the csv files located. Defaults to 'reports'
 * - outputDir - where to output the generated graphical reports. Defaults to 'reports'
 * - chartType - the type of the generated chart, allowed values being 'throughput' and 'putGet'. defaults to
 * 'throughput'
 * </pre>
 *
 * @author Mircea.Markus@jboss.com
 */
public class GenerateChartStage extends AbstractMasterStage {

   private static Log log = LogFactory.getLog(GenerateChartStage.class);

   private String chartType = "throughput";
   private String reportDirectory = "reports";
   private String outputDir = "reports";
   private String fnPrefix;
   private Map<String, List<String>> reportFilters = new HashMap<String, List<String>>();

   public boolean execute() {
      ChartGen gen;

      if (chartType.equalsIgnoreCase("putget")) {
         gen = new PutGetChartGenerator();
      } else {
         gen = new ThroughputChartGenerator();
      }

      gen.setReportDirectory(reportDirectory);
      gen.setOutputDir(outputDir);
      gen.setFileNamePrefix(fnPrefix);
      for (Map.Entry<String, List<String>> entry : reportFilters.entrySet()) {
         String prodName = entry.getKey();
         for (String prodConfig : entry.getValue()) {
            gen.addToReportFilter(prodName, Utils.fileName2Config(prodConfig));
         }
      }
      try {
         gen.generateChart();
         return true;
      } catch (IOException e) {
         log.warn(e);
         return false;
      }
   }

   public void setFnPrefix(String fnPrefix) {
      this.fnPrefix = fnPrefix;
   }

   public void setReportDirectory(String reportDirectory) {
      this.reportDirectory = reportDirectory;
   }

   public void setChartType(String chartType) {
      this.chartType = chartType;
   }

   public void setOutputDir(String outputDir) {
      this.outputDir = outputDir;
   }

   public void addReportFilter(String prodName, String config) {
      List<String> stringList = reportFilters.get(prodName);
      if (stringList == null) {
         stringList = new ArrayList<String>();
         reportFilters.put(prodName, stringList);
      }
      stringList.add(config);
   }
}
