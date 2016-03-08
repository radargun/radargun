package org.radargun.reporting.html;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTick;
import org.jfree.chart.axis.TickType;
import org.jfree.chart.axis.ValueTick;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.chart.util.TextUtils;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.TextAnchor;

/**
 * Chart showing the values as connected lines.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class LineChart extends ComparisonChart {
   protected final YIntervalSeriesCollection dataset = new YIntervalSeriesCollection();
   protected final Map<Comparable, YIntervalSeries> seriesMap = new HashMap<>();
   protected final SortedMap<Double, String> iterationValues = new TreeMap<>();
   protected double upperRange = 0;

   public LineChart(String domainLabel, String rangeLabel) {
      super(domainLabel, rangeLabel);
   }

   @Override
   protected JFreeChart createChart() {
      JFreeChart chart = ChartFactory.createXYLineChart(null, domainLabel, rangeLabel,
         dataset, PlotOrientation.VERTICAL, true, false, false);
      DeviationRenderer renderer = new DeviationRenderer();
      renderer.setAlpha(0.2f);
      chart.getXYPlot().setRenderer(renderer);
      CategorizedAxis domainAxis = new CategorizedAxis();
      domainAxis.setLabel(domainLabel);
      chart.getXYPlot().setDomainAxis(domainAxis);
      if (upperRange > 0) {
         chart.getXYPlot().getRangeAxis().setRange(0, upperRange * 1.1);
      }
      for (int i = 0; i < chart.getXYPlot().getSeriesCount(); ++i) {
         renderer.setSeriesFillPaint(i, DEFAULT_PAINTS[i % DEFAULT_PAINTS.length]);
      }
      return chart;
   }

   @Override
   public void addValue(double value, double deviation, Comparable seriesName, double xValue, String xString) {
      YIntervalSeries series = seriesMap.get(seriesName);
      if (series == null) {
         seriesMap.put(seriesName, series = new YIntervalSeries(seriesName));
         dataset.addSeries(series);
      }
      // don't let the chart scale according to deviation (values wouldn't be seen)
      upperRange = Math.max(upperRange, Math.min(value + deviation, 3 * value));
      series.add(xValue, value, value - deviation, value + deviation);
      if (xString != null) {
         iterationValues.put(xValue, xString);
      }
   }

   private class CategorizedAxis extends NumberAxis {
      @Override
      protected List refreshTicksHorizontal(Graphics2D g2, Rectangle2D dataArea, RectangleEdge edge) {
         List<ValueTick> result = new ArrayList<>();

         Font tickLabelFont = getTickLabelFont();
         g2.setFont(tickLabelFont);

         if (isAutoTickUnitSelection()) {
            selectAutoTickUnit(g2, dataArea, edge);
         }

         double maxUsedPosition = 0;
         for (Map.Entry<Double, String> value : iterationValues.tailMap(getRange().getLowerBound()).headMap(getRange().getUpperBound()).entrySet()) {
            double xPosition = valueToJava2D(value.getKey(), dataArea, edge);
            if (xPosition > maxUsedPosition) {
               result.add(new NumberTick(TickType.MAJOR, value.getKey(), value.getValue(), TextAnchor.TOP_CENTER, TextAnchor.CENTER, 0.0));
               Rectangle2D bounds = TextUtils.getTextBounds(value.getValue(), g2.getFontMetrics());
               maxUsedPosition = xPosition + bounds.getWidth() * 1.1;
            }
         }
         return result;
      }
   }
}