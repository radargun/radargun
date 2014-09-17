package org.radargun.reporting.html;

import org.jfree.data.statistics.DefaultStatisticalCategoryDataset;

/**
 * Wraps JFree charts for use in reports. Currently, 2 types of statistics are
 * displayed in form of a chart - throughput (ops/sec) and mean/standard deviation.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class ComparisonChart extends Chart {
   protected final String domainLabel;
   protected final String rangeLabel;
   protected final DefaultStatisticalCategoryDataset categorySet = new DefaultStatisticalCategoryDataset();

   public ComparisonChart(String domainLabel, String rangeLabel) {
      this.rangeLabel = rangeLabel;
      this.domainLabel = domainLabel;
   }

   public void addValue(double value, double deviation, Comparable rowKey, Comparable columnKey) {
      categorySet.add(value, deviation, rowKey, columnKey);
   }
}
