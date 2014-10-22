package org.radargun.reporting.html;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.radargun.Service;
import org.radargun.config.Cluster;
import org.radargun.config.ComplexDefinition;
import org.radargun.config.Configuration;
import org.radargun.config.Definition;
import org.radargun.config.SimpleDefinition;
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

   private int elementCounter = 0;

   public IndexDocument(String directory) {
      super(directory, "index.html", "RadarGun benchmark");
   }

   @Override
   public void open() throws IOException {
      super.open();
      writeTag("h1", "RadarGun benchmark report");
   }

   @Override
   protected void writeScripts() {
      write("function switch_visibility (id) {\n");
      write("    var element = document.getElementById(id);\n");
      write("    if (element == null) return;\n");
      write("    if (element.style.visibility == 'collapse') {\n");
      write("        element.style.visibility = 'visible';\n");
      write("    } else {\n");
      write("         element.style.visibility = 'collapse';\n");
      write("    }\n}\n");
      write("function switch_li_display (id) {\n");
      write("    var element = document.getElementById(id);\n");
      write("    if (element == null) return;\n");
      write("    if (element.style.display == 'none') {\n");
      write("        element.style.display = 'list-item';\n");
      write("    } else {\n");
      write("         element.style.display = 'none';\n");
      write("    }\n}\n");
   }

   public void writeConfigurations(Collection<Report> reports) {
      writeTag("h2", "Configurations");
      write("The benchmark was executed on following configurations and cluster sizes:<br>\n");
      for (Report report : reports) {
         writeTag("strong", report.getConfiguration().name);
         write(" on cluster with " + report.getCluster().getSize() + " slaves: " + report.getCluster());
         write("\n<br>Setups:<br><ul>\n");
         for (Configuration.Setup setup : report.getConfiguration().getSetups()) {
            write(String.format("<li>Group %s:<ul>\n<li>Plugin: %s</li>\n<li>Service: %s</li>\n", setup.group, setup.plugin, setup.service));
            writeConfigurationFiles(report, setup);
            if (!setup.getProperties().isEmpty()) {
               write("<li>Properties: <ul>\n");
               for (Map.Entry<String, Definition> property : setup.getProperties().entrySet()) {
                  writeProperty(property.getKey(), property.getValue());
               }
               write("</ul></li>\n");
            }
            write("</ul></li>\n");
         }
         write("</ul><br>\n");
      }

   }

   private void writeProperty(String name, Definition definition) {
      if (definition instanceof SimpleDefinition) {
         write(String.format("<li>%s: %s</li>\n", name, definition));
      } else if (definition instanceof ComplexDefinition) {
         write("<li>" + name + ":<ul>\n");
         for (ComplexDefinition.Entry property : ((ComplexDefinition) definition).getAttributes()) {
            writeProperty(property.name, property.definition);
         }
         write("</ul></li>\n");
      }
   }

   private static class OriginalConfig {
      private Set<Integer> slaves = new HashSet<>();
      private String filename;
      private byte[] content;

      public OriginalConfig(int slave, String filename, byte[] content) {
         slaves.add(slave);
         this.filename = filename;
         this.content = content;
      }
   }

   private void writeConfigurationFiles(Report report, Configuration.Setup setup) {
      Set<Integer> slaves = report.getCluster().getSlaves(setup.group);
      Set<OriginalConfig> configs = new HashSet<>();
      for (Map.Entry<Integer, Map<String, byte[]>> entry : report.getOriginalServiceConfig().entrySet()) {
         if (slaves.contains(entry.getKey()) && entry.getValue() != null) {
            for (Map.Entry<String, byte[]> file : entry.getValue().entrySet()) {
               addToConfigs(configs, entry.getKey(), file.getKey(), file.getValue());
            }
         }
      }
      String file = String.valueOf(setup.getProperties().get(Service.FILE));
      if (configs.size() == 0) {
         write("<li>Configuration file: " + file + "</li>\n");
      } else if (configs.size() == 1) {
         write("<li>Configuration file: ");
         writeConfig(report.getCluster(), setup, configs.iterator().next(), false);
         write("</li>\n");
      } else {
         write("<li>Configuration files: <ul>\n");
         for (OriginalConfig config : configs) {
            if (config.filename.equals(file)) {
               write("<li>");
               writeConfig(report.getCluster(), setup, config, config.slaves.size() != slaves.size());
               write("</li>\n");
            }
         }
         for (OriginalConfig config : configs) {
            if (!config.filename.equals(file)) {
               write("<li>");
               writeConfig(report.getCluster(), setup, config, config.slaves.size() != slaves.size());
               write("</li>\n");
            }
         }
         write("</ul>");
      }
      Set<String> normalized = new HashSet<>();
      for (Map.Entry<Integer, Map<String, Properties>> entry : report.getNormalizedServiceConfigs().entrySet()) {
         if (slaves.contains(entry.getKey()) && entry.getValue() != null) {
            normalized.addAll(entry.getValue().keySet());
         }
      }
      write("<li>Normalized configurations: <ul>\n");
      for (String config : normalized) {
         write(String.format("<li><a href=\"%s\">%s</a></li>",
               NormalizedConfigDocument.getFilename(
                     report.getConfiguration().name, setup.group, report.getCluster(), config), config));
      }
      write("</ul></li>");
   }

   private void writeConfig(Cluster cluster, Configuration.Setup setup, OriginalConfig config, boolean writeSlaves) {
      String configFile = String.format("original_%s_%s_%d_%s",
            setup.getConfiguration().name, setup.group, cluster.getClusterIndex(), config.filename).replace(File.separator, "_");
      FileOutputStream contentWriter = null;
      boolean written = false;
      try {
         contentWriter = new FileOutputStream(directory + File.separator + configFile);
         try {
            contentWriter.write(config.content);
         } catch (IOException e) {
            log.error("Failed to write " + configFile, e);
         } finally {
            try {
               contentWriter.close();
               written = true;
            } catch (IOException e) {
               log.error("Failed to close", e);
            }
         }
      } catch (FileNotFoundException e) {
         log.error("Failed to open " + configFile, e);
      }
      if (written) {
         write(String.format("<a href=\"%s\">%s</a>", configFile, config.filename));
      } else {
         write(config.filename);
      }
      if (writeSlaves) {
         write("(slaves ");
         boolean first = true;
         for (int slave : config.slaves) {
            write(String.valueOf(slave));
            if (!first) {
               write(", ");
            }
            first = false;
         }
         write(")");
      }

   }

   private void addToConfigs(Set<OriginalConfig> configs, int slave, String filename, byte[] content) {
      boolean found = false;
      for (OriginalConfig config : configs) {
         if (config.filename.equals(filename) && Arrays.equals(config.content, content)) {
            config.slaves.add(slave);
            found = true;
            break;
         }
      }
      if (!found) {
         configs.add(new OriginalConfig(slave, filename, content));
      }
   }

   public void writeScenario(Collection<Report> reports) {
      if (reports.isEmpty()) return;
      // all reports should share the same stages
      List<Report.Stage> stages = reports.iterator().next().getStages();
      writeTag("h2", "Scenario");
      write("Note that some properties may have not been resolved correctly as these depend on local properties<br>");
      write("\nThese stages have been used for the benchmark:<br><ul>\n");
      for (Report.Stage stage : stages) {
         writeStage(stage);
      }
      write("</ul>\n");
   }

   private void writeStage(Report.Stage stage) {
      writer.write("<li><span style=\"cursor: pointer\" onClick=\"");
      List<Report.Property> properties = stage.getProperties();
      for (int i = 0; i < properties.size(); ++i) {
         writer.write("switch_li_display('e" + (elementCounter + i) + "');");
      }
      writer.write(String.format("\">%s</span><ul>\n", stage.getName()));
      for (Report.Property property : properties) {
         int propertyCounter = elementCounter++;
         if (property.getDefinition() != null) {
            writer.write(String.format("<li><strong>%s = %s</strong>&nbsp;" +
                  "<small style=\"cursor: pointer\"  id=\"showdef%d\" onClick=\"switch_visibility('showdef%d'); switch_visibility('def%d');\">show definition</small>" +
                  "<span id=\"def%d\" style=\"visibility: collapse\"><small style=\"cursor: pointer\" onClick=\"switch_visibility('showdef%d'); switch_visibility('def%d');\">hide definition:</small>" +
                  "&nbsp;%s</span></li>\n", property.getName(), escape(String.valueOf(property.getValue())),
                  propertyCounter, propertyCounter, propertyCounter, propertyCounter, propertyCounter, propertyCounter,
                  escape(String.valueOf(property.getDefinition()))));
         } else {
            writer.write(String.format("<li id=\"e%d\" style=\"display: none\"><small>%s = %s</small></li>\n",
                     propertyCounter, property.getName(), property.getValue()));
         }
      }
      writer.write("</ul></li>\n");
   }

   private String escape(String str) {
      return str.replaceAll("<", "&lt;").replace(">", "&gt;");
   }

   protected void writeTimelines(Collection<Report> reports) {
      writeTag("h2", "Timelines");
      write("<ul>\n");
      for (Report report : reports) {
         write(String.format("<li><a href=\"timeline_%s_%d.html\">%s on %s</a></li>",
            report.getConfiguration().name, report.getCluster().getClusterIndex(), report.getConfiguration().name, report.getCluster()));
      }
      write("\n</ul>");
   }

   protected void writeTests(Collection<String> testNames) {
      writeTag("h2", "Tests");
      write("<ul>\n");
      for (String test : testNames) {
         write(String.format("<li><a href=\"test_%s.html\">%s</a></li>", test, test));
      }
      write("\n</ul>");
   }

   protected void writeFooter() {
      writer.write("<hr>");
      writer.write("Generated on " + new Date() + " by RadarGun\nJDK: " +
            System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.version") + ", " +
            System.getProperty("java.vm.vendor") + ") OS: " + System.getProperty("os.name") + " (" +
            System.getProperty("os.version") + ", " + System.getProperty("os.arch") + ")");
   }
}
