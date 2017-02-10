package org.radargun.reporting.html;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.MasterConfig;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;

/**
 * The main document in HTML report.
 * Shows benchmark configuration and links to timeline & test results.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class IndexDocument extends HtmlDocument {
   private static final Log log = LogFactory.getLog(IndexDocument.class);
   private Map<Report, Map<String, Set<OriginalConfig>>> configs = new HashMap<>();
   private Set<String> normalized = new HashSet<>();
   private MasterConfig masterConfig;

   public IndexDocument(String directory) {
      super(directory, "index.html", "RadarGun benchmark");
   }

   public void writeMasterConfig(MasterConfig masterConfig) {
      this.masterConfig = masterConfig;
      String configFile = "master-config.xml";
      String scenarioFile = "scenario-file.xml";
      try (FileOutputStream mainFileWriter = new FileOutputStream(directory + File.separator + configFile);
           FileOutputStream scenarioWriter = new FileOutputStream(directory + File.separator + scenarioFile)
      ) {
         mainFileWriter.write(masterConfig.getMasterConfigBytes());
         if (masterConfig.getScenarioBytes() != null) {
            scenarioWriter.write(masterConfig.getScenarioBytes());
         }
      } catch (FileNotFoundException e) {
         log.error("Failed to open " + configFile, e);
      } catch (IOException e) {
         log.error("Failed to write " + configFile, e);
      }
   }

   /**
    * Used from freemarker template to decide whether to provide a link to an external scenario file.
    * @return true if there's an external scenario file imported into the main RG benchmark
    */
   public boolean isExternalScenario() {
      return masterConfig.getScenarioBytes() != null;
   }

   private void writeConfig(Cluster cluster, Configuration.Setup setup, OriginalConfig config) {
      String configFile = String.format("original_%s_%s_%d_%s",
         setup.getConfiguration().name, setup.group, cluster.getClusterIndex(), config.filename).replace(File.separator, "_");
      try (FileOutputStream contentWriter = new FileOutputStream(directory + File.separator + configFile)) {
         contentWriter.write(config.content);
      } catch (FileNotFoundException e) {
         log.error("Failed to open " + configFile, e);
      } catch (IOException e) {
         log.error("Failed to write " + configFile, e);
      }
   }

   private void addToConfigs(Map<String, Set<OriginalConfig>> configs, String group, int slave, String filename, byte[] content) {
      boolean found = false;
      for (OriginalConfig config : configs.get(group)) {
         if (config.filename.equals(filename) && Arrays.equals(config.content, content)) {
            config.slaves.add(slave);
            found = true;
            break;
         }
      }
      if (!found) {
         configs.get(group).add(new OriginalConfig(slave, filename, content));
      }
   }

   /**
    * Prepares configuration files for report
    *
    * @param reports to be parsed
    */
   public void prepareServiceConfigs(Collection<Report> reports) {
      for (Report report : reports) {
         Map<String, Set<OriginalConfig>> configs = new HashMap<>();
         for (Configuration.Setup setup : report.getConfiguration().getSetups()) {
            Set<Integer> slaves = report.getCluster().getSlaves(setup.group);
            if (configs.get(setup.group) == null) {
               configs.put(setup.group, new HashSet<>());
            }
            for (Map.Entry<Integer, Map<String, Properties>> entry : report.getNormalizedServiceConfigs().entrySet()) {
               if (slaves.contains(entry.getKey()) && entry.getValue() != null) {
                  normalized.addAll(entry.getValue().keySet());
               }
            }

            for (Map.Entry<Integer, Map<String, byte[]>> entry : report.getOriginalServiceConfig().entrySet()) {
               if (slaves.contains(entry.getKey()) && entry.getValue() != null) {
                  for (Map.Entry<String, byte[]> file : entry.getValue().entrySet()) {
                     addToConfigs(configs, setup.group, entry.getKey(), file.getKey(), file.getValue());
                  }
               }
            }
            this.configs.put(report, configs);

            for (OriginalConfig config : configs.get(setup.group)) {
               writeConfig(report.getCluster(), setup, config);
            }
         }
      }
   }

   /**
    * The following methods are used in Freemarker templates
    * e.g. method getPercentiles() can be used as getPercentiles() or percentiles in template
    */

   public Set<OriginalConfig> getConfigs(Report report, String groupName) {
      return configs.get(report).get(groupName);
   }

   public String getFilename(String configName, String groupName, Cluster cluster, String config) {
      return String.format("normalized_%s_%s_%d_%s.html",
         configName, groupName, cluster.getClusterIndex(), config);
   }

   public Set<String> getNormalized() {
      return normalized;
   }

   public String removeFileSeparator(String string) {
      return string.replace(File.separator, "_");
   }

   public static class OriginalConfig {
      private Set<Integer> slaves = new HashSet<>();
      private String filename;
      private byte[] content;

      public OriginalConfig(int slave, String filename, byte[] content) {
         slaves.add(slave);
         this.filename = filename;
         this.content = content;
      }

      /**
       * The following methods are used in Freemarker templates
       * e.g. method getPercentiles() can be used as getPercentiles() or percentiles in template
       */

      public Set<Integer> getSlaves() {
         return slaves;
      }

      public String getFilename() {
         return filename;
      }

      public byte[] getContent() {
         return content;
      }
   }
}
