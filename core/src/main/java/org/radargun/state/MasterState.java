package org.radargun.state;

import org.radargun.config.MasterConfig;
import org.radargun.reporting.Report;
import org.radargun.reporting.Timeline;

/**
 * State residing on the server, passed to each stage before execution.
 *
 * @author Mircea.Markus@jboss.com
 */
public class MasterState extends StateBase {
   private MasterConfig config;
   private Report report;

   public MasterState(MasterConfig config) {
      this.config = config;
   }

   public MasterConfig getConfig() {
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
