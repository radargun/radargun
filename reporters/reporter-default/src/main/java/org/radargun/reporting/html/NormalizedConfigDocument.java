package org.radargun.reporting.html;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.radargun.config.Cluster;

/**
 * Presents normalized properties from service configuration.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class NormalizedConfigDocument extends HtmlDocument {
   private SortedMap<String, SortedMap<Integer, String>> properties = new TreeMap<>();
   private Set<Integer> slaves;

   public NormalizedConfigDocument(String directory, String configName, String groupName, Cluster cluster, String config,
                                   Map<Integer, Map<String, Properties>> configs, Set<Integer> slaves) {
      super(directory, getFilename(configName, groupName, cluster, config), getTitle(configName, groupName, cluster, config));
      this.slaves = slaves;
      for (Map.Entry<Integer, Map<String, Properties>> entry : configs.entrySet()) {
         if (slaves.contains(entry.getKey())) {
            Properties p = entry.getValue().get(config);
            if (p != null) {
               for (Map.Entry<Object, Object> property : p.entrySet()) {
                  SortedMap<Integer, String> m = properties.get(property.getKey());
                  if (m == null) {
                     properties.put(String.valueOf(property.getKey()), m = new TreeMap<>());
                  }
                  m.put(entry.getKey(), String.valueOf(property.getValue()));
               }
            }
         }
      }
   }

   private static String getTitle(String configName, String groupName, Cluster cluster, String config) {
      return String.format("Normalized configuration %s for %s, group %s on %s",
            config, configName, groupName, cluster);
   }

   public static String getFilename(String configName, String groupName, Cluster cluster, String config) {
      return String.format("normalized_%s_%s_%d_%s.html",
            configName, groupName, cluster.getClusterIndex(), config);
   }

   @Override
   protected void writeStyle() {
      write("TABLE { border-spacing: 0; border-collapse: collapse; }\n");
      write("TD { border: 1px solid gray; padding: 2px; }\n");
      write("TH { border: 1px solid gray; padding: 2px; text-align: left; }\n");
      write(".difference { background-color: #FFBBBB; }\n");
   }

   public void writeProperties() {
      write("<table><tr><th>&nbsp;</th>");
      for (Integer slave : slaves) {
         write("<th style=\"text-align: center\">Slave " + slave + "</th>");
      }
      write("</tr>");
      for (Map.Entry<String, SortedMap<Integer, String>> property : properties.entrySet()) {
         String firstValue = null;
         boolean difference = false;
         for (String value : property.getValue().values()) {
            if (firstValue == null) {
               firstValue = value;
            } else if (!firstValue.equals(value)) {
               difference = true;
            }
         }
         write("<tr>");
         if (difference) {
            write("<th class=\"difference\">" + property.getKey() + "</th>");
         } else {
            writeTag("th", property.getKey());
         }
         for (String value : property.getValue().values()) {
            if (difference) {
               write("<td class=\"difference\">" + value + "</td>");
            } else {
               writeTag("td", value);
            }
         }
         write("</tr>\n");
      }
      write("</table>");
   }
}
