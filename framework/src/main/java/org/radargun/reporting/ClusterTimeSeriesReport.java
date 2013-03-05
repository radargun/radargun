package org.radargun.reporting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.radargun.sysmonitor.AbstractActivityMonitor;

/**
 * Data object to hold the contents of a TimeSeries chart from an AbstractActivityMonitor object
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class ClusterTimeSeriesReport {

   private TimeSeriesCollection categorySet = new TimeSeriesCollection();
   private String xLabel;
   private String yLabel;
   private String title;
   private String subtitle;
   private List<String> notes = new ArrayList<String>();

   public void init(String xLabel, String yLabel, String title, String subtitle) {
      this.xLabel = xLabel;
      this.yLabel = yLabel;
      this.title = title;
      this.subtitle = subtitle;
   }

   public void addSeries(TimeSeries newSeries) {
      this.categorySet.addSeries(newSeries);
   }

   public TimeSeries generateSeries(String seriesName, AbstractActivityMonitor monitor) {
      TimeSeries newSeries = new TimeSeries(seriesName, Second.class);
      List<BigDecimal> measurements = monitor.getMeasurements();
      /*
       * TODO: Current assumes measurements happen every second. Should really be based on
       * LocalJmxMonitor.MEASURING_FREQUENCY, but they are the same right now.
       */
      RegularTimePeriod timeScale = null;
      for (BigDecimal value : measurements) {
         if (timeScale == null) {
            timeScale = RegularTimePeriod.createInstance(Second.class, new Date(monitor.getFirstMeasurementTime()),
                  TimeZone.getDefault());
            newSeries.add(timeScale, value);
         } else {
            newSeries.add(newSeries.getNextTimePeriod(), value);
         }
      }
      return newSeries;
   }

   public void addNote(String note) {
      notes.add(note);
   }

   public String getTitle() {
      return title;
   }

   public String getSubtitle() {
      return subtitle;
   }

   public String getXLabel() {
      return xLabel;
   }

   public String getYLabel() {
      return yLabel;
   }

   public List<String> getNotes() {
      return notes;
   }

   public TimeSeriesCollection getCategorySet() {
      return categorySet;
   }
}
