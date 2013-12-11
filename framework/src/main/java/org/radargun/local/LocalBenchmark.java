package org.radargun.local;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.CacheWrapperStressor;
import org.radargun.ShutDownHook;
import org.radargun.reporting.LocalSystemMonitorChart;
import org.radargun.state.SlaveState;
import org.radargun.stressors.AbstractCacheWrapperStressor;
import org.radargun.sysmonitor.LocalJmxMonitor;
import org.radargun.utils.TypedProperties;
import org.radargun.utils.Utils;

/**
 * @author Mircea.Markus@jboss.com
 */
public class LocalBenchmark {

   private static Log log = LogFactory.getLog(LocalBenchmark.class);

   private static final String LOCAL_BENCHMARK_CSV = "local_benchmark.csv";
   private static final String REPORTS_DIR = "reports";
   private static final boolean SKIP_FREE_MEMORY_CHECK = Boolean.getBoolean("radargun.skip_free_memory_check");

   private List<CacheWrapperStressor> stressors = new ArrayList<CacheWrapperStressor>();
   private LinkedHashMap<String, List<Properties>> product2Config = new LinkedHashMap<String, List<Properties>>();
   private boolean headerGenerted = false;
   private List<ReportDesc> reportDescs = new ArrayList<ReportDesc>();

   private long initialFreeMemory = freeMememory();

   private StringBuilder reportCsvContent = new StringBuilder();

   private Map<String, LocalJmxMonitor> sysMonitors = new HashMap<String, LocalJmxMonitor>();

   public LocalBenchmark() {
      Runtime.getRuntime().addShutdownHook(new ShutDownHook("Local benchmark process"));
   }

   public void benchmark() throws Exception {
      log.info("Starting benchmark with " + Utils.kb(initialFreeMemory) + " kb initial free memory.");
      for (Map.Entry<String, List<Properties>> product : product2Config.entrySet()) {
         for (Properties configProps : product.getValue()) {
            final String config = configProps.getProperty("name");
            log.info("Processing " + product.getKey() + "-" + config);
            CacheWrapper wrapper = getCacheWrapper(product.getKey(), configProps.getProperty("wrapper"));
            try {
               wrapper.setUp(config, true, -1, new TypedProperties(configProps));

               Map<String, Object> results = null;
               SlaveState slaveState = new SlaveState();
               for (CacheWrapperStressor stressor : stressors) {
                  LocalJmxMonitor monitor = null;
                  if (stressor.isSysMonitorEnabled()) {
                     monitor = new LocalJmxMonitor();
                     monitor.startMonitoringLocal();
                     sysMonitors.put(product.getKey() + "(" + config + ")", monitor);
                     monitor.setConfigName(config);
                     monitor.setProductName(product.getKey());
                  }
                  ((AbstractCacheWrapperStressor)stressor).setSlaveState(slaveState);

                  results = stressor.stress(wrapper);
                  if (monitor != null) {
                     monitor.stopMonitoringLocal();
                  }
                  stressor.destroy();
                  wrapper.clear(true);
               }
               generateReport(results, product.getKey(), config);
               wrapper.tearDown();
               wrapper = null;
               gc();
            } catch (Exception e) {
               log.error("Error processing " + product.getKey() + "-" + config, e);
               wrapper.tearDown();
            }
         }
      }
      createOutputFile();

      generateChart();

      generateMonitorReports();
   }

   private void generateMonitorReports() {
      if (sysMonitors.isEmpty()) {
         return;
      }
      for (ReportDesc reportDesc : reportDescs) {
         new LocalSystemMonitorChart(sysMonitors).generate(reportDesc);
      }
   }

   private void generateChart() throws Exception {
      for (ReportDesc reportDesc : reportDescs) {
         LocalChartGenerator chartGenerator = new LocalChartGenerator(reportDesc);
         chartGenerator.generateChart(REPORTS_DIR);
      }
   }

   private void gc() throws Exception {
      log.info(Utils.printMemoryFootprint(true));
      for (int i = 0; i < 30; i++) {
         System.gc();
         if (!freeMemoryOkay()) {
            log.info("GC didn't finish the work. Initial free memory was: " + Utils.kb(initialFreeMemory) + "kb, now we have: " + Utils.kb(freeMememory()) + "kb");
            Thread.sleep(1000);
         } else {
            break;
         }
      }
      if (!freeMemoryOkay()) {
         log.error("The amount of free memory is more than 10% smaller than original one, benchmarks might be affected. Exiting... ");
         //ShutDownHook.exit(1);
      }
      log.info(Utils.printMemoryFootprint(false));
   }

   private boolean freeMemoryOkay() {
      return !SKIP_FREE_MEMORY_CHECK && (freeMememory() + 0.1 * freeMememory() >= initialFreeMemory);
   }

   private void createOutputFile() throws IOException {
      File parentDir = new File(REPORTS_DIR);
      if (!parentDir.exists()) {
         if (!parentDir.mkdirs())
            throw new RuntimeException(parentDir.getAbsolutePath() + " does not exist and could not be created!");
      }

      File reportFile = Utils.createOrReplaceFile(parentDir, LOCAL_BENCHMARK_CSV);
      if (!reportFile.exists()) {
         throw new IllegalStateException(reportFile.getAbsolutePath() + " was deleted? Not allowed to delete report file during test run!");
      }
      PrintWriter writer = null;
      try {
         writer = new PrintWriter(reportFile);
         writer.append(reportCsvContent.toString());
      } finally {
         if (writer != null) writer.close();
      }

   }

   private void generateReport(Map<String, Object> results, String product, String config) throws IOException {
      generateHeader(results);
      StringBuilder line = new StringBuilder();
      line.append(product).append(",").append(config);
      for (String key : results.keySet()) {
         line.append(",").append(results.get(key));
      }
      writeLine(line.toString());
      for (ReportDesc reportDesc : reportDescs) {
         if(results.get("READS_PER_SEC") != null && results.get("WRITE_COUNT") != null) {
            long readsPerSec = (long) (double) (Double) results.get("READS_PER_SEC");
            long noReads = (Long) results.get("READ_COUNT");
            long writesPerSec = (long) (double) (Double) results.get("WRITES_PER_SEC");
            long noWrites = (Long) results.get("WRITE_COUNT");
            reportDesc.updateData(product, config, readsPerSec, noReads, writesPerSec, noWrites);
         }
      }
   }

   private void generateHeader(Map<String, Object> results) throws FileNotFoundException {
      if (!headerGenerted) {
         StringBuilder header = new StringBuilder();
         header.append("PRODUCT, CONFIG");
         for (String key : results.keySet()) {
            header.append(",").append(key);
         }
         writeLine(header.toString());
         headerGenerted = true;
      }
   }

   private void writeLine(String line) throws FileNotFoundException {
      reportCsvContent.append('\n').append(line);
   }

   private CacheWrapper getCacheWrapper(String product, String fqnClass) throws Exception {
      if(fqnClass == null) {
         fqnClass = Utils.getCacheWrapperFqnClass(product);
      }

      URLClassLoader loader = Utils.buildProductSpecificClassLoader(product, getClass().getClassLoader());
      Thread.currentThread().setContextClassLoader(loader);
      return (CacheWrapper) loader.loadClass(fqnClass).newInstance();
   }

   public void addStressor(CacheWrapperStressor stressor) {
      stressors.add(stressor);
   }

   public void addProductConfig(String productName, List<Properties> configs) {
      product2Config.put(productName, configs);
   }

   public void addReportDesc(ReportDesc reportDesc) {
      reportDescs.add(reportDesc);
   }

   private long freeMememory() {
      return Runtime.getRuntime().freeMemory();
   }
}
