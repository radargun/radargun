package org.radargun.state;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.radargun.config.MasterConfig;
import org.radargun.reporting.Report;
import org.radargun.reporting.Timeline;

/**
 * State residing on the server, passed to each stage before execution.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class MasterState extends StateBase {
   private MasterConfig config;
   private Report report;
   private List<MasterListener> listeners = new CopyOnWriteArrayList<MasterListener>();

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

   public void addListener(MasterListener listener) {
      listeners.add(listener);
   }

   public void removeListener(MasterListener listener) {
      listeners.remove(listener);
   }

   public List<MasterListener> getListeners() {
      return Collections.unmodifiableList(listeners);
   }
}
