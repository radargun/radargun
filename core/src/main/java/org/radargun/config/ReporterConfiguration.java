package org.radargun.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ReporterConfiguration {
   public final String type;
   public final RunCondition run;
   public final List<Report> reports = new ArrayList<Report>();

   public ReporterConfiguration(String type, RunCondition run) {
      this.type = type;
      this.run = run;
   }

   public Report addReport() {
      Report report = new Report();
      reports.add(report);
      return report;
   }

   public List<Report> getReports() {
      return Collections.unmodifiableList(reports);
   }

   public class Report {
      private final Map<String, String> properties = new HashMap<String, String>();

      public void addProperty(String name, String value) {
         if (properties.put(name, value) != null) {
            throw new IllegalArgumentException("Property '" + name + "' already defined!");
         }
      }

      public Map<String, String> getProperties() {
         return Collections.unmodifiableMap(properties);
      }
   }

   public enum RunCondition {
      ALWAYS,
      CLUSTER_SUCCESSFUL,
      CONFIG_SUCCESSFUL,
      ALL_SUCCESSFUL
   }
}
