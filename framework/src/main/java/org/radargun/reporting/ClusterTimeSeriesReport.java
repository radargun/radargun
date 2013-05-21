package org.radargun.reporting;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.jfree.data.time.Hour;
import org.jfree.data.time.Minute;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.radargun.sysmonitor.AbstractActivityMonitor;
import org.radargun.sysmonitor.LocalJmxMonitor;

/**
 * Data object to hold the contents of a TimeSeries chart from an AbstractActivityMonitor object
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class ClusterTimeSeriesReport extends AbstractClusterReport {

   private TimeSeriesCollection categorySet = new TimeSeriesCollection();

   public void addSeries(TimeSeries newSeries) {
      this.categorySet.addSeries(newSeries);
   }

   public TimeSeries generateSeries(String seriesName, AbstractActivityMonitor monitor) {
      Class<?> timePeriod = null;

      if (LocalJmxMonitor.measuringUnit.equals(TimeUnit.SECONDS)) {
         timePeriod = Second.class;
      }
      if (LocalJmxMonitor.measuringUnit.equals(TimeUnit.MINUTES)) {
         timePeriod = Minute.class;
      }
      if (LocalJmxMonitor.measuringUnit.equals(TimeUnit.HOURS)) {
         timePeriod = Hour.class;
      }
      TimeSeries newSeries = new TimeSeries(seriesName, timePeriod);

      List<BigDecimal> measurements = monitor.getMeasurements();

      RegularTimePeriod timeScale = null;
      for (BigDecimal value : measurements) {
         if (timeScale == null) {
            Calendar date = new GregorianCalendar();
            // reset hour, minutes, seconds and millis
            date.set(Calendar.HOUR_OF_DAY, 0);
            date.set(Calendar.MINUTE, 0);
            date.set(Calendar.SECOND, 0);
            date.set(Calendar.MILLISECOND, 0);

            timeScale = RegularTimePeriod.createInstance(timePeriod, date.getTime(), TimeZone.getDefault());
            newSeries.add(timeScale, value);
         } else {
            newSeries.add(newSeries.getNextTimePeriod(), value);
         }
      }
      return newSeries;
   }

   public TimeSeriesCollection getCategorySet() {
      return categorySet;
   }
}
