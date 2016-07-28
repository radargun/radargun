package org.radargun.reporting.html;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.jfree.chart.ChartColor;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.encoders.EncoderUtil;
import org.jfree.chart.encoders.ImageFormat;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.Minute;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.TextAnchor;
import org.radargun.config.Converter;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;

/**
 * Chart showing the events, intervals and values from {@link Timeline}
 */
public class TimelineChart {
   private static final Log log = LogFactory.getLog(TimelineChart.class);
   private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
   private static final Paint[] DEFAULT_PAINTS = ChartColor.createDefaultPaintArray();
   private static final int LABEL_OFFSET = 15;
   private static final int DOMAIN_OFFSET = 3;
   private static final int MAX_EVENT_VALUES = 1000;

   private int width = 1024;
   private int height = 768;
   private Class<? extends RegularTimePeriod> timePeriodClass = Second.class;

   private Paint paint;
   //private Shape shape;
   private Stroke stroke = new BasicStroke(1);

   private long startTimestamp;
   private long endTimestamp;
   private JFreeChart chart;

   public TimelineChart() {
      this(Color.RED);
   }

   public TimelineChart(Color paint) {
      this.paint = paint;
   }

   public void setEvents(List<? extends Timeline.Event> events, int slaveIndex, long startTimestamp, long endTimestamp, double lowerBound, double upperBound) {
      int paintIndex = slaveIndex % DEFAULT_PAINTS.length;
      if (paintIndex < 0) paintIndex += DEFAULT_PAINTS.length;
      paint = DEFAULT_PAINTS[paintIndex];
      this.startTimestamp = startTimestamp;
      this.endTimestamp = endTimestamp + (startTimestamp == endTimestamp ? 1 : 0);
      TimeSeries series = new TimeSeries("Slave " + slaveIndex);
      TimeSeriesCollection dataset = new TimeSeriesCollection(series, GMT);
      chart = ChartFactory.createTimeSeriesChart(null, "Time from start", null, dataset, false, false, false);
      chart.setBackgroundPaint(new Color(0, 0, 0, 0));
      XYPlot plot = chart.getXYPlot();
      plot.getRenderer().setSeriesPaint(0, paint);
      //plot.getRenderer().setSeriesShape(0, shape);
      plot.setBackgroundAlpha(0);
      plot.setDomainGridlinesVisible(false);
      plot.setDomainZeroBaselineVisible(true);
      plot.setRangeGridlinesVisible(false);
      plot.setRangeZeroBaselineVisible(true);

      Number[] minValues = new Number[MAX_EVENT_VALUES];
      Number[] maxValues = new Number[MAX_EVENT_VALUES];
      long[] minTimestamps = new long[MAX_EVENT_VALUES];
      long[] maxTimestamps = new long[MAX_EVENT_VALUES];
      //long lastTimestamp = Long.MIN_VALUE;
      for (Timeline.Event event : events) {
         if (event instanceof Timeline.Value) {
            Timeline.Value value = (Timeline.Value) event;
            int bucket = (int) ((event.timestamp - startTimestamp) * MAX_EVENT_VALUES / (endTimestamp - startTimestamp));
            if (minValues[bucket] == null) {
               minValues[bucket] = value.value;
               maxValues[bucket] = value.value;
               minTimestamps[bucket] = event.timestamp;
               maxTimestamps[bucket] = event.timestamp;
            } else {
               if (minValues[bucket].doubleValue() > value.value.doubleValue()) {
                  minValues[bucket] = value.value;
               }
               if (maxValues[bucket].doubleValue() < value.value.doubleValue()) {
                  maxValues[bucket] = value.value;
               }
               minTimestamps[bucket] = Math.min(minTimestamps[bucket], event.timestamp);
               maxTimestamps[bucket] = Math.max(maxTimestamps[bucket], event.timestamp);
            }
         } else if (event instanceof Timeline.IntervalEvent) {
            Timeline.IntervalEvent intervalEvent = (Timeline.IntervalEvent) event;
            IntervalMarker marker = new IntervalMarker(event.timestamp - startTimestamp, event.timestamp + intervalEvent.duration - startTimestamp, paint, stroke, paint, stroke, 0.3f);
            marker.setLabel(intervalEvent.description);
            marker.setLabelAnchor(RectangleAnchor.BOTTOM);
            marker.setLabelTextAnchor(TextAnchor.BOTTOM_CENTER);
            marker.setLabelOffset(new RectangleInsets(0, 0, (slaveIndex + 1) * LABEL_OFFSET, 0));
            plot.addDomainMarker(marker);
         } else if (event instanceof Timeline.TextEvent) {
            ValueMarker marker = new ValueMarker(event.timestamp - startTimestamp, paint, stroke);
            marker.setLabel(((Timeline.TextEvent) event).text);
            marker.setLabelAnchor(RectangleAnchor.BOTTOM_LEFT);
            marker.setLabelTextAnchor(TextAnchor.BOTTOM_LEFT);
            marker.setLabelOffset(new RectangleInsets(0, 0, (slaveIndex + 1) * LABEL_OFFSET, 0));
            plot.addDomainMarker(marker);
         }
      }
      for (int bucket = 0; bucket < MAX_EVENT_VALUES; ++bucket) {
         if (minValues[bucket] == null) continue;
         series.addOrUpdate(time(minTimestamps[bucket] - startTimestamp), minValues[bucket]);
         series.addOrUpdate(time(maxTimestamps[bucket] - startTimestamp), maxValues[bucket]);
      }

      DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
      dateAxis.setTimeZone(GMT);
      dateAxis.setMinimumDate(new Date(0));
      dateAxis.setMaximumDate(new Date(endTimestamp - startTimestamp));
      if (upperBound > lowerBound) {
         plot.getRangeAxis().setRange(lowerBound, upperBound);
      }
   }

   public void saveChart(String filename) throws IOException {
      try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
         chart.getXYPlot().getRangeAxis().setVisible(false);
         chart.getXYPlot().getDomainAxis().setVisible(false);
         ChartRenderingInfo renderingInfo = new ChartRenderingInfo();
         BufferedImage image = chart.createBufferedImage(width, height, renderingInfo);
         EncoderUtil.writeBufferedImage(image, ImageFormat.PNG, out);
      }
   }

   public void saveRange(String filename) throws IOException {
      try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
         chart.getXYPlot().getRangeAxis().setVisible(true);
         chart.getXYPlot().getDomainAxis().setVisible(false);
         ChartRenderingInfo renderingInfo = new ChartRenderingInfo();
         BufferedImage image = chart.createBufferedImage(width, height, renderingInfo);
         image = image.getSubimage(0, 0, (int) renderingInfo.getPlotInfo().getDataArea().getX(), height);
         EncoderUtil.writeBufferedImage(image, ImageFormat.PNG, out);
      }
   }

   public void saveRelativeDomain(String filename) throws IOException {
      try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
         chart.getXYPlot().getRangeAxis().setVisible(false);
         chart.getXYPlot().getDomainAxis().setVisible(true);
         ChartRenderingInfo renderingInfo = new ChartRenderingInfo();
         BufferedImage image = chart.createBufferedImage(width, height, renderingInfo);
         int maxY = (int) renderingInfo.getPlotInfo().getDataArea().getMaxY();
         image = image.getSubimage(0, maxY + DOMAIN_OFFSET, width, height - maxY - DOMAIN_OFFSET);
         EncoderUtil.writeBufferedImage(image, ImageFormat.PNG, out);
      }
   }

   public void saveAbsoluteDomain(String filename) throws IOException {
      try (OutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
         chart.getXYPlot().getRangeAxis().setVisible(false);
         Font labelFont = chart.getXYPlot().getDomainAxis().getLabelFont();
         chart.getXYPlot().setDomainAxis(new DateAxis("Time"));
         chart.getXYPlot().getDomainAxis().setRange(startTimestamp, endTimestamp);
         chart.getXYPlot().getDomainAxis().setLabelFont(labelFont);
         ChartRenderingInfo renderingInfo = new ChartRenderingInfo();
         BufferedImage image = chart.createBufferedImage(width, height, renderingInfo);
         int maxY = (int) renderingInfo.getPlotInfo().getDataArea().getMaxY();
         image = image.getSubimage(0, maxY + DOMAIN_OFFSET, width, height - maxY - DOMAIN_OFFSET);
         EncoderUtil.writeBufferedImage(image, ImageFormat.PNG, out);
      }
   }

   private RegularTimePeriod time(long timestamp) {
      Date date = new Date(timestamp);
      return RegularTimePeriod.createInstance(timePeriodClass, date, GMT);
   }

   public static int getColorForIndex(int slaveIndex) {
      if (slaveIndex < 0) return 0;
      return ((Color) DEFAULT_PAINTS[slaveIndex % DEFAULT_PAINTS.length]).getRGB() & 0xFFFFFF;
   }

   public void setDimensions(int width, int height) {
      this.width = width;
      this.height = height;
   }

   private static class TimeUnitConverter implements Converter<Class<? extends RegularTimePeriod>> {
      @Override
      public Class<? extends RegularTimePeriod> convert(String string, Type type) {
         if (string == null) throw new NullPointerException();
         string = string.toLowerCase(Locale.ENGLISH);
         if ("millisecond".equals(string)) {
            return Millisecond.class;
         }
         if ("second".equals(string)) {
            return Second.class;
         }
         if ("minute".equals(string)) {
            return Minute.class;
         }
         if ("hour".equals(string)) {
            return Hour.class;
         }
         throw new IllegalArgumentException(string);
      }

      @Override
      public String convertToString(Class<? extends RegularTimePeriod> value) {
         return value.getSimpleName().toLowerCase(Locale.ENGLISH);
      }

      @Override
      public String allowedPattern(Type type) {
         return "millisecond|second|minute|hour";
      }
   }
}
