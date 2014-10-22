package org.radargun.reporting.html;

/**
 * Wraps JFree charts for use in reports. Currently, 2 types of statistics are
 * displayed in form of a chart - throughput (ops/sec) and mean/standard deviation.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class ComparisonChart extends Chart {
   protected final String domainLabel;
   protected final String rangeLabel;

   public ComparisonChart(String domainLabel, String rangeLabel) {
      this.rangeLabel = rangeLabel;
      this.domainLabel = domainLabel;
   }

   public abstract void addValue(double value, double deviation, Comparable seriesName, double xValue, String xString);
}
