package org.radargun.reporting.html;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.radargun.Stage;
import org.radargun.config.Configuration;
import org.radargun.config.Definition;
import org.radargun.config.Path;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Scenario;
import org.radargun.config.StageHelper;
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
            write(String.format("<li>Group %s -> Plugin: %s, Service: %s, Configuration file: %s", setup.group, setup.plugin, setup.service, setup.file));
            if (!setup.getProperties().isEmpty()) {
               write(", Properties: <ul>\n");
               for (Map.Entry<String, String> property : setup.getProperties().entrySet()) {
                  writeTag("li", String.format("%s: %s", property.getKey(), property.getValue()));
               }
               write("</ul>\n");
            }
            write("</li>\n");
         }
         write("</ul><br>\n");
      }

   }

   public void writeScenario(Scenario scenario) {
      writeTag("h2", "Scenario");
      write("Note that some properties may have not been resolved correctly as these depend on local properties<br>");
      write("\nThese stages have been used for the benchmark:<br><ul>\n");
      for (int i = 0; i < scenario.getStageCount(); ++i) {
         writeStage(scenario.getStage(i, Collections.EMPTY_MAP), scenario.getPropertiesDefinitions(i));
      }
      write("</ul>\n");
   }

   private void writeStage(Stage stage, Map<String, Definition> propertiesDefinitions) {
      Class<? extends Stage> stageClass = stage.getClass();
      writer.write("<li><span style=\"cursor: pointer\" onClick=\"");
      Set<Map.Entry<String, Path>> properties = PropertyHelper.getProperties(stageClass, true, false).entrySet();
      for (int i = 0; i < properties.size(); ++i) {
         writer.write("switch_li_display('e" + (elementCounter + i) + "');");
      }
      writer.write(String.format("\">%s</span><ul>\n", StageHelper.getStageName(stageClass)));
      for (Map.Entry<String, Path> property : properties) {
         Path path = property.getValue();
         String currentValue = PropertyHelper.getPropertyString(path, stage);
         int propertyCounter = elementCounter++;
         if (propertiesDefinitions.containsKey(property.getKey())) {
            writer.write(String.format("<li><strong>%s = %s</strong>&nbsp;" +
                  "<small style=\"cursor: pointer\"  id=\"showdef%d\" onClick=\"switch_visibility('showdef%d'); switch_visibility('def%d');\">show definition</small>" +
                  "<span id=\"def%d\" style=\"visibility: collapse\"><small style=\"cursor: pointer\" onClick=\"switch_visibility('showdef%d'); switch_visibility('def%d');\">hide definition:</small>" +
                  "&nbsp;%s</span></li>\n", property.getKey(), currentValue,
                  propertyCounter, propertyCounter, propertyCounter, propertyCounter, propertyCounter, propertyCounter,
                  propertiesDefinitions.get(property.getKey())));
         } else {
            writer.write(String.format("<li id=\"e%d\" style=\"display: none\"><small>%s = %s</small></li>\n",
                     propertyCounter, property.getKey(), currentValue));
         }
      }
      writer.write("</ul></li>\n");
   }

   protected void writeTimelines(Collection<Report> reports) {
      writeTag("h2", "Timelines");
      write("<ul>\n");
      Map<String, Integer> clusterIndices = new HashMap<String, Integer>();
      for (Report report : reports) {
         Integer clusterIndex = clusterIndices.get(report.getConfiguration().name);
         if (clusterIndex == null) clusterIndex = 0;
         clusterIndices.put(report.getConfiguration().name, clusterIndex + 1);
         write(String.format("<li><a href=\"timeline_%s_%d.html\">%s on %s</a></li>",
               report.getConfiguration().name, clusterIndex, report.getConfiguration().name, report.getCluster()));
      }
      write("\n</ul>");
   }

   protected void writeTests(Collection<Report> reports) {
      writeTag("h2", "Tests");
      TreeSet<String> testNames = new TreeSet<String>();
      for (Report report : reports) {
         for (Report.Test test : report.getTests()) {
            testNames.add(test.name);
         }
      }
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
