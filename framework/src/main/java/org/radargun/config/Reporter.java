package org.radargun.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Reporter {
   public final String type;
   public final RunCondition run;
   public final List<Report> reports = new ArrayList<Report>();

   public Reporter(String type, RunCondition run) {
      this.type = type;
      this.run = run;
   }

   public Report addReport(String source) {
      Report report = new Report(source);
      reports.add(report);
      return report;
   }

   public List<Report> getReports() {
      return Collections.unmodifiableList(reports);
   }

   public class Report {
      public final String source;
      private final Map<String, String> properties = new HashMap<String, String>();

      public Report(String source) {
         this.source = source;
      }

      public void addProperty(String name, String value) {
         if (properties.put(name, value) != null) {
            throw new IllegalArgumentException("Property '" + name + "' already defined!");
         }
      }
   }

   public enum RunCondition {
      ALWAYS,
      CONFIG_SUCCESSFUL,
      CLUSTERS_SUCCESSFUL,
      ALL_SUCCESSFUL
   }
}
