package org.radargun.reporting.html;

import java.util.*;

import org.radargun.config.Cluster;

/**
 * Presents normalized properties from service configuration.
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

   /**
    * The following methods are used in Freemarker templates
    * e.g. method getPercentiles() can be used as getPercentiles() or percentiles in template
    */

   public Map<String, SortedMap<Integer, String>> getProperties() {
      return properties;
   }

   public Set<Integer> getSlaves() {
      return slaves;
   }

   public boolean checkForDifference(List<String> values) {
      String firstValue = null;

      for (String value : values) {
         if (firstValue == null) {
            firstValue = value;
         } else if (!firstValue.equals(value)) {
            return true;
         }
      }
      return false;
   }
}
