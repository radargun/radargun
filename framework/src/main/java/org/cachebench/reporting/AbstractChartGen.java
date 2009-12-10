package org.cachebench.reporting;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.chart.title.TextTitle;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

public abstract class AbstractChartGen implements ChartGen {

   private static Log log = LogFactory.getLog(AbstractChartGen.class);

   protected String reportDirectory;
   protected String outputDir;
   protected boolean backupExistingFile = false;
   protected static final String MU = "\u00B5";//((char) 0xC2B5) + "";
   protected Map<String, List<String>> filter = new HashMap<String, List<String>>();
   protected Map<String, List<Pattern>> compiledFilters = null;
   protected String filenamePrefix;

   public void setOutputDir(String outputDir) {
      this.outputDir = outputDir;
   }

   public void setBackupExistingFile(boolean backupExistingFile) {
      this.backupExistingFile = backupExistingFile;
   }

   public void setReportDirectory(String reportDirectory) {
      this.reportDirectory = reportDirectory;

   }

   public void addToReportFilter(String productName, String productConfig) {
      List<String> list = filter.get(productName);
      if (list == null) {
         list = new ArrayList<String>();
         filter.put(productName, list);
      }
      list.add(productConfig);
   }

   public boolean isUsingFilters() {
      return !filter.isEmpty();
   }

   public void setFileNamePrefix(String fileNamePrefix) {
      this.filenamePrefix = fileNamePrefix;
   }

   public static TextTitle getSubtitle() {
      return new TextTitle("Generated on " + new Date() + " by The CacheBenchFwk\nJDK: " +
            System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.version") + ", " +
            System.getProperty("java.vm.vendor") + ") OS: " + System.getProperty("os.name") + " (" +
            System.getProperty("os.version") + ", " + System.getProperty("os.arch") + ")");
   }

   protected File[] getFilteredFiles(File file) {
      return file.listFiles(new FilenameFilter() {
         //accepted file names are <product-name>_<config-name>_<cluster-size>.csv
         public boolean accept(File dir, String name) {
            compileFilters();
            if (!name.toUpperCase().endsWith(".CSV")) {
               return false;
            }
            if (!isUsingFilters()) {
               return true;
            }
            StringTokenizer tokenizer = new StringTokenizer(name, "_");
            String productName = tokenizer.nextToken();
            String configName = tokenizer.nextToken();
            if (!filter.containsKey(productName)) return false;

            //first check that this works as compilation issues might have occured during parsing the patterns
            if (filter.get(productName).contains(configName)) {
               return true;
            } else {
               List<Pattern> patternList = compiledFilters.get(productName);
               for (Pattern pattern : patternList) {
                  if (pattern.matcher(configName).find()) {
                     log.trace("Pattern '" + pattern + "' matched config: " + configName);
                     return true;
                  }
               }
            }
            return false;
         }
      });
   }

   private void compileFilters() {
      if (compiledFilters == null) {
         compiledFilters = new HashMap<String, List<Pattern>>();
         for (String product : filter.keySet()) {
            List<Pattern> compiled = new ArrayList<Pattern>();
            for (String aFilter : filter.get(product)) {
               try {
                  Pattern pattern = Pattern.compile(aFilter);
                  compiled.add(pattern);
               } catch (Exception e) {
                  log.warn("Exception while compiling the pattern: '" + aFilter + "'", e);
               }
            }
            compiledFilters.put(product, compiled);
         }
      }
   }

}
