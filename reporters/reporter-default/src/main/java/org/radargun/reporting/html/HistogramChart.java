package org.radargun.reporting.html;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberTick;
import org.jfree.chart.axis.Tick;
import org.jfree.chart.axis.TickType;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.text.TextUtilities;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.TextAnchor;
import org.radargun.stats.representation.Histogram;

/**
 * Writes bar plot with time (in nanoseconds) on logarithmic X-axis and percents on Y-axis
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HistogramChart extends Chart {
   private double left;
   private double right;
   private XYDataset dataset;

   /**
    * @param operation Name of the plotted operation
    * @param histogram Histogram that should be plotted
    */
   public HistogramChart setData(String operation, Histogram histogram)  {
      XYSeries series = new XYSeries(operation + " response times");
      long totalCount = 0;
      for (long count : histogram.counts) {
         totalCount += count;
      }
      left = histogram.ranges[0];
      right = histogram.ranges[histogram.ranges.length - 1];

      for (int i = 0; i < histogram.counts.length; i++) {
         series.add(histogram.ranges[i], (double) histogram.counts[i] / totalCount);
      }
      series.add(right, 0d);
      dataset = new XYSeriesCollection(series);
      return this;
   }

   protected JFreeChart createChart() {
      JFreeChart chart = ChartFactory.createXYStepAreaChart(null, "Response time", "Percentage", dataset,
                                                       PlotOrientation.VERTICAL, false, false, false);
      XYPlot plot = (XYPlot) chart.getPlot();
      HistogramLogAxis xAxis = new HistogramLogAxis("Response time");
      xAxis.setRange(left, right);
      plot.setDomainAxis(xAxis);
      return chart;
   }

   private static class HistogramLogAxis extends LogAxis {

      protected static final double TICK_LABEL_ANGLE = Math.PI / 4;

      private HistogramLogAxis(String label) {
         super(label);
      }

      @Override

      protected List refreshTicksHorizontal(Graphics2D g2, Rectangle2D dataArea, RectangleEdge edge) {
         Range range = getRange();
         List ticks = new ArrayList();
         Font tickLabelFont = getTickLabelFont();
         g2.setFont(tickLabelFont);
         TextAnchor textAnchor;
         if (edge == RectangleEdge.TOP) {
            textAnchor = TextAnchor.BOTTOM_RIGHT;
         } else {
            textAnchor = TextAnchor.TOP_LEFT;
         }

         double start = Math.pow(10, Math.floor(Math.log10(range.getLowerBound())));
         double end = Math.pow(10, Math.floor(Math.log10(range.getUpperBound())));
         double current = start;
         while (current <= end) {
            if (range.contains(current)) {
               ticks.add(new NumberTick(TickType.MAJOR, current, tickLabel(current), textAnchor, textAnchor, TICK_LABEL_ANGLE));
            }
            for (int i = 2; i < 10; ++i) {
               if (range.contains(current * i)) {
                  ticks.add(new NumberTick(TickType.MINOR, current * i, tickLabel(current * i), textAnchor, TextAnchor.TOP_LEFT, TICK_LABEL_ANGLE));
               }
            }
            current *= 10;
         }
         return ticks;
      }

      protected double findMaximumTickLabelHeight(List ticks, Graphics2D g2, Rectangle2D drawArea, boolean vertical) {
         RectangleInsets insets = getTickLabelInsets();
         Font font = getTickLabelFont();
         g2.setFont(font);
         double maxHeight = 0.0;
         Iterator iterator = ticks.iterator();
         while (iterator.hasNext()) {
            Tick tick = (Tick) iterator.next();
            Rectangle2D labelBounds = null;
            if (tick.getText() != null) {
               labelBounds = TextUtilities.calculateRotatedStringBounds(tick.getText(), g2, 0, 0, TextAnchor.TOP_LEFT, TICK_LABEL_ANGLE, TextAnchor.TOP_LEFT).getBounds2D();
            }
            if (labelBounds != null && labelBounds.getWidth()
                  + insets.getTop() + insets.getBottom() > maxHeight) {
               maxHeight = labelBounds.getWidth()
                     + insets.getTop() + insets.getBottom();
            }
         }
         return maxHeight;
      }

      protected double findMaximumTickLabelWidth(List ticks, Graphics2D g2, Rectangle2D drawArea, boolean vertical) {
         RectangleInsets insets = getTickLabelInsets();
         double maxWidth = 0.0;
         Iterator iterator = ticks.iterator();
         while (iterator.hasNext()) {
            Tick tick = (Tick) iterator.next();
            Rectangle2D labelBounds = null;
            if (tick.getText() != null) {
               labelBounds = TextUtilities.calculateRotatedStringBounds(tick.getText(), g2, 0, 0, TextAnchor.TOP_LEFT, TICK_LABEL_ANGLE, TextAnchor.TOP_LEFT).getBounds2D();
            }
            if (labelBounds != null
                  && labelBounds.getWidth() + insets.getLeft()
                  + insets.getRight() > maxWidth) {
               maxWidth = labelBounds.getWidth()
                     + insets.getLeft() + insets.getRight();
            }
         }
         return maxWidth;
      }

      protected String tickLabel(double value) {
         String unit;
         if (value < 1000d) {
            unit = "ns";
         } else if (value < 1000000d) {
            unit = "us";
            value /= 1000d;
         } else {
            unit = "ms";
            value /= 1000000d;
         }
         return String.format("%.0f %s", value, unit);
      }
   }
}
