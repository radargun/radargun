package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.data.statistics.DefaultStatisticalCategoryDataset;

/**
 * Wraps JFree charts for use in reports. Currently, 2 types of statistics are
 * displayed in form of a chart - throughput (ops/sec) and mean/standard deviation.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class ComparisonChart {
   private int width;
   private int height;
   protected final String domainLabel;
   protected final String rangeLabel;

   protected DefaultStatisticalCategoryDataset categorySet = new DefaultStatisticalCategoryDataset();

   public ComparisonChart(String domainLabel, String rangeLabel) {
      this.domainLabel = domainLabel;
      this.rangeLabel = rangeLabel;
   }

   public void addValue(double value, double deviation, Comparable rowKey, Comparable columnKey) {
      categorySet.add(value, deviation, rowKey, columnKey);
   }

   public void save(String filename) throws IOException {
      JFreeChart chart = createChart();
      ChartUtilities.saveChartAsPNG(new File(filename), chart, width, height);
   }

   protected abstract JFreeChart createChart();

   public void setWidth(int width) {
      this.width = width;
   }

   public void setHeight(int height) {
      this.height = height;
   }
}
