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
import org.radargun.config.Property;
import org.radargun.reporting.Timeline;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class TimelineChart {
   protected static final TimeZone GMT = TimeZone.getTimeZone("GMT");

   private final static Paint[] defaultPaints = ChartColor.createDefaultPaintArray();
   private static final int LABEL_OFFSET = 15;
   private static final int DOMAIN_OFFSET = 3;

   @Property(doc = "Width of the chart in pixels. Default is 1024.")
   private int width = 1024;

   @Property(doc = "Height of the chart in pixels. Default is 768.")
   private int height = 768;

   @Property(doc = "Time unit for the horizontal axis. Default is seconds.", converter = TimeUnitConverter.class)
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
      int paintIndex = slaveIndex % defaultPaints.length;
      if (paintIndex < 0) paintIndex += defaultPaints.length;
      paint = defaultPaints[paintIndex];
      this.startTimestamp = startTimestamp;
      this.endTimestamp = endTimestamp;
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

      for (Timeline.Event event : events) {
         if (event instanceof Timeline.Value) {
            Timeline.Value value = (Timeline.Value) event;
            series.add(time(event.timestamp - startTimestamp), value.value);
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

      DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
      dateAxis.setTimeZone(GMT);
      dateAxis.setMinimumDate(new Date(0));
      dateAxis.setMaximumDate(new Date(endTimestamp - startTimestamp));
      plot.getRangeAxis().setRange(lowerBound, upperBound);
   }

   public void saveChart(String filename) throws IOException {
      OutputStream out = new BufferedOutputStream(new FileOutputStream(filename));
      try {
         chart.getXYPlot().getRangeAxis().setVisible(false);
         chart.getXYPlot().getDomainAxis().setVisible(false);
         ChartRenderingInfo renderingInfo = new ChartRenderingInfo();
         BufferedImage image = chart.createBufferedImage(width, height, renderingInfo);
         EncoderUtil.writeBufferedImage(image, ImageFormat.PNG, out);
      } finally {
         out.close();
      }
   }

   public void saveRange(String filename) throws IOException {
      OutputStream out = new BufferedOutputStream(new FileOutputStream(filename));
      try {
         chart.getXYPlot().getRangeAxis().setVisible(true);
         chart.getXYPlot().getDomainAxis().setVisible(false);
         ChartRenderingInfo renderingInfo = new ChartRenderingInfo();
         BufferedImage image = chart.createBufferedImage(width, height, renderingInfo);
         image = image.getSubimage(0, 0, (int) renderingInfo.getPlotInfo().getDataArea().getX(), height);
         EncoderUtil.writeBufferedImage(image, ImageFormat.PNG, out);
      } finally {
         out.close();
      }
   }

   public void saveRelativeDomain(String filename) throws IOException {
      OutputStream out = new BufferedOutputStream(new FileOutputStream(filename));
      try {
         chart.getXYPlot().getRangeAxis().setVisible(false);
         chart.getXYPlot().getDomainAxis().setVisible(true);
         ChartRenderingInfo renderingInfo = new ChartRenderingInfo();
         BufferedImage image = chart.createBufferedImage(width, height, renderingInfo);
         int maxY = (int) renderingInfo.getPlotInfo().getDataArea().getMaxY();
         image = image.getSubimage(0, maxY + DOMAIN_OFFSET, width, height - maxY - DOMAIN_OFFSET);
         EncoderUtil.writeBufferedImage(image, ImageFormat.PNG, out);
      } finally {
         out.close();
      }
   }

   public void saveAbsoluteDomain(String filename) throws IOException {
      OutputStream out = new BufferedOutputStream(new FileOutputStream(filename));
      try {
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
      } finally {
         out.close();
      }
   }

   private RegularTimePeriod time(long timestamp) {
      Date date = new Date(timestamp);
      return RegularTimePeriod.createInstance(timePeriodClass, date, GMT);
   }

   public static class TimeUnitConverter implements Converter<Class<? extends RegularTimePeriod>> {
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
         return ".*";
      }
   }
}
