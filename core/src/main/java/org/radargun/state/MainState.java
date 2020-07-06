package org.radargun.state;

import org.radargun.config.MainConfig;
import org.radargun.reporting.Report;
import org.radargun.reporting.Timeline;

/**
 * State residing on the server, passed to each stage before execution.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class MainState extends StateBase<MainListener> {
   private MainConfig config;
   private Report report;

   public MainState(MainConfig config) {
      this.config = config;
   }

   public MainConfig getConfig() {
      return config;
   }

   public Report getReport() {
      return report;
   }

   public void setReport(Report report) {
      this.report = report;
   }

   public Timeline getTimeline() {
      return report.getTimelines().get(0);
   }

}
