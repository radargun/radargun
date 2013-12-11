/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.reporting;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.Stage;
import org.radargun.config.FixedSizeBenchmarkConfig;
import org.radargun.config.MasterConfig;
import org.radargun.config.PropertyHelper;
import org.radargun.config.ScalingBenchmarkConfig;
import org.radargun.config.StageHelper;
import org.radargun.stressors.SimpleStatistics;

public class HtmlReportGenerator {

   private static Log log = LogFactory.getLog(HtmlReportGenerator.class);

   private BenchmarkResult result;
   private final String directory;
   private final String prefix;
   private final MasterConfig config;
   private PrintWriter writer;
   private int elementCounter = 0;

   public HtmlReportGenerator(MasterConfig config, BenchmarkResult result, String directory, String prefix) {
      this.config = config;
      this.result = result;
      this.directory = directory;
      this.prefix = prefix;
   }

   public void generate() throws IOException {
      try {
         writer = new PrintWriter(directory + File.separator + prefix + "_report.html");
         writer.print("<HTML><HEAD><TITLE>");
         writer.print("Report");
         writer.print("</TITLE><STYLE>\n");
         writer.print("TABLE { border-spacing: 0; border-collapse: collapse; }\n");
         writer.print("TD { border: 1px solid gray; padding: 2px; }\n");
         writer.print("TH { padding: 2px; }\n");
         writer.print("</STYLE>\n<SCRIPT>\n");
         writer.print("function switch_visibility (id) {\n");
         writer.print("    var element = document.getElementById(id);\n");
         writer.print("    if (element.style.visibility == 'collapse') {\n");
         writer.print("        element.style.visibility = 'visible';\n");
         writer.print("    } else {\n");
         writer.print("         element.style.visibility = 'collapse';\n");
         writer.print("    }\n}\n");
         writer.print("function switch_li_display (id) {\n");
         writer.print("    var element = document.getElementById(id);\n");
         writer.print("    if (element.style.display == 'none') {\n");
         writer.print("        element.style.display = 'list-item';\n");
         writer.print("    } else {\n");
         writer.print("         element.style.display = 'none';\n");
         writer.print("    }\n}\n");
         writer.print("</SCRIPT></HEAD>\n<BODY>");
         printTag("h1", "RadarGun benchmark report");
         if (config != null) {
            reportDescription();
         }
         printTag("h2", "Benchmark results");
         writer.write("For closer explanation of these numbers see <a href=\"https://github.com/radargun/radargun/wiki/Understanding-StressTest-results\">RadarGun wiki/Understanding StressTest results</a>");
         for (String requestType : result.getRequestTypes()) {
            reportRequestType(requestType);
         }
         reportJVMStats();
         writer.write("<hr>");
         writer.write("Generated on " + new Date() + " by RadarGun\nJDK: " +
               System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.version") + ", " +
               System.getProperty("java.vm.vendor") + ") OS: " + System.getProperty("os.name") + " (" +
               System.getProperty("os.version") + ", " + System.getProperty("os.arch") + ")");
         writer.println("</BODY></HTML>");
      } finally {
         if (writer != null) writer.close();
      }
   }

   private void reportDescription() {
      printTag("h2", "Benchmark description");
      writer.write("The benchmark was executed on following products, configurations and cluster sizes:\n<ul>");
      for (FixedSizeBenchmarkConfig benchmark : config.getBenchmarks()) {
         if ("report".equals(benchmark.getProductName())) continue;

         StringBuilder sb = new StringBuilder();
         sb.append("<li>Product ").append(benchmark.getProductName());
         sb.append(", configuration ").append(benchmark.getConfigName());
         if (benchmark instanceof ScalingBenchmarkConfig) {
            ScalingBenchmarkConfig scaling = (ScalingBenchmarkConfig) benchmark;
            if (scaling.getIncrement() == 0) {
               sb.append(", ").append(scaling.getInitSize());
            } else {
               for (int i = scaling.getInitSize(); i <= scaling.getMaxSize(); i += scaling.getIncrement()) {
                  sb.append(", ").append(i);
               }
            }
         } else {
            sb.append(", ").append(benchmark.getMaxSize());
         }
         writer.write(sb.append(" nodes</li>\n").toString());
      }
      writer.print("</ul>\nThese stages have been used for the benchmark:\n<ul>");
      for (Stage stage : config.getBenchmarks().get(0).getStages()) {
         reportStage(stage);
      }
      writer.print("</ul>\n");
   }

   private void reportJVMStats() {
      printTag("h2", "JVM Statistics");
      for (String config : result.getConfigs()) {
         for (int clusterSize : result.getClusterSizes()) {
            String name = String.format("%s on %d node(s)", config, clusterSize);
            boolean hasJvmStats = false;
            for (String stats : new String[] { "-cpu_usage.png", "-memory_usage.png", "-gc_usage.png"}) {
               if (new File(directory + File.separator + name + stats).exists()) {
                  if (!hasJvmStats) {
                     printTag("h3", name);
                     hasJvmStats = true;
                  }
                  printImg(name + stats, "");
               }
            }
         }
      }
   }

   private void reportStage(Stage stage) {
      Class<? extends Stage> stageClass = stage.getClass();
      Stage defaultStage = null;
      try {
         defaultStage = stageClass.newInstance();
      } catch (Exception e) {
         log.error("Cannot instantiate default stage for " + stageClass.getName(), e);
      }
      writer.write("<li style=\"cursor: pointer\" onClick=\"");
      Set<Map.Entry<String,Field>> properties = PropertyHelper.getProperties(stageClass).entrySet();
      for (int i = 0; i < properties.size(); ++i) {
         writer.write("switch_li_display('e" + (elementCounter + i) + "');");
      }
      writer.write(String.format("\">%s<ul>", StageHelper.getStageName(stageClass)));
      for (Map.Entry<String, Field> property : properties) {
         Field propertyField = property.getValue();
         String currentValue = PropertyHelper.getPropertyString(propertyField, stage);
         if (defaultStage == null) {
            writer.write(String.format("<li><strong>%s = %s</strong></li>\n", property.getKey(), currentValue));
         } else {
            String defaultValue = PropertyHelper.getPropertyString(propertyField, defaultStage);
            if (defaultValue == currentValue || (defaultValue != null && defaultValue.equals(currentValue))) {
               writer.write(String.format("<li id=\"e%d\" style=\"display: none\"><small>%s = %s</small></li>",
                     elementCounter++, property.getKey(), currentValue));
            } else {
               writer.write(String.format("<li><strong>%s = %s</strong></li>\n", property.getKey(), currentValue));
            }
         }
      }
      writer.write("</ul></li>");
   }

   private void reportRequestType(String requestType) {
      printTag("h3", requestType);

      /* Table data */
      reportTableData(requestType);

      /* Charts */
      printImg(String.format("%s_%s_TX.png", prefix, requestType), "Including tx overhead");
      printImg(String.format("%s_%s_NET.png", prefix, requestType), "Without tx overhead");

      /* Histograms */
      reportHistograms(requestType);

      writer.write("<hr>");
   }

   private void reportHistograms(String requestType) {
      Set<Integer> allIterations = result.getAllIterations();
      Set<Integer> clusterSizes = result.getClusterSizes();
      boolean histogramFound = false;
      hist_search: for (String config : result.getConfigs()) {
         for (int clusterSize : clusterSizes) {
            for (int iteration: allIterations) {
               if (result.getHistograms(config, clusterSize, iteration).get(requestType + "_TX") != null ||
                   result.getHistograms(config, clusterSize, iteration).get(requestType + "_NET") != null) {
                  histogramFound = true;
                  break hist_search;
               }
            }
         }
      }
      if (!histogramFound) return;

      printTag("h5", "Histograms");
      writer.write("<table>\n<tr><th colspan=\"2\">&nbsp;</th>");
      for (int clusterSize : clusterSizes) {
         for (int iteration : allIterations) {
            writer.write(String.format("<th>%d node(s), iteration %d</th>", clusterSize, iteration));
         }
      }
      writer.write("</tr>\n");
      for (String config : result.getConfigs()) {
         writer.write("<tr><th rowspan=\"2\">");
         writer.write(config);
         writer.write("</th><th>incl. tx</th>");
         for (int clusterSize : clusterSizes) {
            for (int iteration: allIterations) {
               HistogramData txHistogram = result.getHistograms(config, clusterSize, iteration).get(requestType + "_TX");
               if (txHistogram == null) {
                  writer.write("<td>&nbsp;</td>");
               } else {
                  writer.write(String.format("<td><img src=\"%s\"/></td>", txHistogram.getFileName(prefix)));
               }
            }
         }
         writer.write("</tr>\n<tr><th>net</th>");
         for (int clusterSize : clusterSizes) {
            for (int iteration: allIterations) {
               HistogramData netHistogram = result.getHistograms(config, clusterSize, iteration).get(requestType + "_NET");
               if (netHistogram == null) {
                  writer.write("<td>&nbsp;</td>");
               } else {
                  writer.write(String.format("<td><img src=\"%s\"/></td>", netHistogram.getFileName(prefix)));
               }
            }
         }
         writer.write("</tr>\n");
      }
      writer.write("</table>\n");
   }

   private void reportTableData(String requestType) {
      Set<Integer> clusterSizes = result.getClusterSizes();
      Set<Integer> allIterations = result.getAllIterations();
      Set<Integer> emptyIterations = new HashSet<Integer>();
      for (int iteration : allIterations) {
         boolean requestsFound = false;
         over_configs: for (String config : result.getConfigs()) {
            for (int clusterSize: clusterSizes) {
               if (result.getAggregatedStats(config, clusterSize, iteration).getNumRequests(requestType) != 0) {
                  requestsFound = true;
                  break over_configs;
               }
            }
         }
         if (!requestsFound) {
            emptyIterations.add(iteration);
         }
      }
      allIterations.removeAll(emptyIterations);

      writer.write("<table>\n<tr><th>&nbsp;</th>");
      for (int clusterSize : clusterSizes) {
         for (int iteration : allIterations) {
            writer.write(String.format("<th colspan=\"6\">%d node(s), iteration %d</th>", clusterSize, iteration));
         }
      }
      writer.write("</tr>\n<tr><th>&nbsp;</th>\n");
      for (int i = clusterSizes.size() * allIterations.size(); i > 0; --i) {
         writer.write("<th style=\"text-align: center\">count</th>\n");
         writer.write("<th style=\"text-align: center\" colspan=\"3\">incl. tx</th>\n");
         writer.write("<th style=\"text-align: center\" colspan=\"3\">without tx (net)</th>\n");
      }
      writer.write("</tr>\n<tr><th>Product, config</th>");
      for (int i = clusterSizes.size() * allIterations.size(); i > 0; --i) {
         writer.write("<th>&nbsp;</th><th>mean</td><th>std.dev<th>throughput</th><th>mean</td><th>std.dev<th>throughput</th>");
      }
      writer.write("</tr>\n");
      for (String config : result.getConfigs()) {
         writer.write("<tr><th style=\"text-align: left; cursor: pointer\" onClick=\"");
         int maxClusterSize = result.getMaxClusterSize();
         for (int i = 0; i < maxClusterSize; ++i) {
            writer.write("switch_visibility('e" + (elementCounter + i) + "'); ");
         }
         writer.write(String.format("\">%s</th>", config));
         boolean first = true;
         for (int clusterSize : clusterSizes) {
            for (int iteration : allIterations) {
               SimpleStatistics stats = result.getAggregatedStats(config, clusterSize, iteration);
               long totalRequests = stats.getNumRequests(requestType);
               boolean suspect = false;
               for (int i = 0; i < clusterSize; ++i) {
                  SimpleStatistics nodeStats = result.getNodeStats(config, clusterSize, iteration, i);
                  if (nodeStats != null) {
                     long nodeRequests = nodeStats.getNumRequests(requestType);
                     if (nodeRequests * clusterSize > totalRequests * 5 / 4 || nodeRequests * clusterSize < totalRequests * 4 / 5) {
                        suspect = true;
                     }
                  }
               }
               int threadCount = result.getThreadCount(config, clusterSize, iteration);
               if (threadCount <= 0) stats = null;
               printTableDataRow(stats, threadCount, requestType, first, false, suspect);
               first = false;
            }
         }
         writer.write("</tr>\n");
         for (int i = 0; i < maxClusterSize; ++i) {
            writer.write(String.format("<tr id=\"e%d\" style=\"visibility: collapse;\"><th style=\"text-align: right\">node%d</th>", elementCounter++, i));
            first = true;
            for (int clusterSize : clusterSizes) {
               for (int iteration : allIterations) {
                  long totalRequests = result.getAggregatedStats(config, clusterSize, iteration).getNumRequests(requestType);
                  SimpleStatistics stats = result.getNodeStats(config, clusterSize, iteration, i);
                  int threadCount = result.getThreadCount(config, clusterSize, iteration);
                  long requests = stats == null ? 0 : stats.getNumRequests(requestType);
                  boolean suspect = requests * clusterSize > totalRequests * 5 / 4 || requests * clusterSize < totalRequests * 4 / 5;
                  printTableDataRow(stats, threadCount, requestType, first, true, suspect);
                  first = false;
               }
            }
            writer.write("</tr>\n");
         }
      }
      writer.write("</table><br>\n");
   }

   private void printTableDataRow(SimpleStatistics stats, int threadCount, String requestType, boolean first, boolean gray, boolean suspect) {
      if (stats == null) {
         printTD("&nbsp;", first ? "" : "border-left-color: black; border-left-width: 4px;");
         printTD("&nbsp;", "border-left-color: black; border-left-width: 2px;");
         printTD("&nbsp;", "");
         printTD("&nbsp;", "");
         printTD("&nbsp;", "border-left-color: black; border-left-width: 2px;");
         printTD("&nbsp;", "");
         printTD("&nbsp;", "");
         return;
      }
      String rowStyle = suspect ? "background-color: #FFBBBB; " : (gray ? "background-color: #F0F0F0; ": "");
      rowStyle += "text-align: right; ";
      SimpleStatistics.MeanAndDev withTx = stats.getMeanAndDev(true, requestType);
      SimpleStatistics.MeanAndDev net = stats.getMeanAndDev(false, requestType);
      printTD(String.valueOf(stats.getNumRequests(requestType)),
            rowStyle + (first ? "" : "border-left-color: black; border-left-width: 4px;"));
      printTD(withTx == null ? "-" : String.format("%.2f ms", toMillis(withTx.mean)),
            rowStyle + "border-left-color: black; border-left-width: 2px;");
      printTD(withTx == null ? "-" : String.format("%.2f ms", toMillis(withTx.dev)), rowStyle);
      printTD(String.format("%.0f reqs/s", threadCount * stats.getOperationsPerSecond(true, requestType)), rowStyle);
      printTD(net == null ? "-" : String.format("%.2f ms", toMillis(net.mean)),
            rowStyle + "border-left-color: black; border-left-width: 2px;");
      printTD(net == null ? "-" : String.format("%.2f ms", toMillis(net.dev)), rowStyle);
      printTD(String.format("%.0f reqs/s", threadCount * stats.getOperationsPerSecond(false, requestType)), rowStyle);
   }

   private void printTD(String content, String style) {
      writer.write("<td style=\"" + style + "\">");
      writer.write(content);
      writer.write("</td>\n");
   }

   private double toMillis(double nanos) {
      return nanos/1000000d;
   }

   private void printTag(String tag, String content) {
      writer.write(String.format("<%s>%s</%s>", tag, content, tag));
   }

   private void printImg(String src, String description) {
      writer.write(String.format("<div style=\"text-align: left; display: inline-block;\"><div style=\"display: table; text-align: center\"><img src=\"%s\" alt=\"%s\"/><br>%s</div></div>\n",
            src, description, description));
   }
}
