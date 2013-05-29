package org.radargun.reporting;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.jfree.data.time.Hour;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.Minute;
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
public class ClusterTimeSeriesReport extends AbstractClusterReport {

   private TimeSeriesCollection categorySet = new TimeSeriesCollection();
   private TimeUnit chartTimeUnit = null;
   private int chartFrequency = 1;
   private int dateUnit;

   public ClusterTimeSeriesReport(int chartFrequency, TimeUnit chartTimeUnit) {
      super();
      this.chartTimeUnit = chartTimeUnit;
      this.chartFrequency = chartFrequency;
   }

   public void addSeries(TimeSeries newSeries) {
      this.categorySet.addSeries(newSeries);
   }

   public TimeSeries generateSeries(String seriesName, AbstractActivityMonitor monitor) {
      Class<?> timePeriod = null;

      if (this.chartTimeUnit.equals(TimeUnit.MILLISECONDS)) {
         timePeriod = Millisecond.class;
         dateUnit = Calendar.MILLISECOND;
      }
      if (this.chartTimeUnit.equals(TimeUnit.SECONDS)) {
         timePeriod = Second.class;
         dateUnit = Calendar.SECOND;
      }
      if (this.chartTimeUnit.equals(TimeUnit.MINUTES)) {
         timePeriod = Minute.class;
         dateUnit = Calendar.MINUTE;
      }
      if (this.chartTimeUnit.equals(TimeUnit.HOURS)) {
         timePeriod = Hour.class;
         dateUnit = Calendar.HOUR;
      }

      if (timePeriod == null) {
         throw new IllegalArgumentException("Time Unit '" + chartTimeUnit.name() + "' is not supported.");
      }

      TimeSeries newSeries = new TimeSeries(seriesName, timePeriod);

      List<BigDecimal> measurements = monitor.getMeasurements();

      RegularTimePeriod timeScale = null;
      int counter = 1;
      Calendar date = new GregorianCalendar();
      // reset hour, minutes, seconds and millis
      date.set(Calendar.HOUR_OF_DAY, 0);
      date.set(Calendar.MINUTE, 0);
      date.set(Calendar.SECOND, 0);
      date.set(Calendar.MILLISECOND, 0);
      for (BigDecimal value : measurements) {
         timeScale = RegularTimePeriod.createInstance(timePeriod, date.getTime(), TimeZone.getDefault());
         newSeries.add(timeScale, value);
         date.add(dateUnit, chartFrequency);
      }
      return newSeries;
   }

   public TimeSeriesCollection getCategorySet() {
      return categorySet;
   }
}
