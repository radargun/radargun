package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.data.statistics.DefaultStatisticalCategoryDataset;
import org.radargun.stats.representation.MeanAndDev;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class ComparisonChart {
   public int width;
   public int height;
   protected DefaultStatisticalCategoryDataset categorySet = new DefaultStatisticalCategoryDataset();

   public void add(String category, Integer clusterSize, MeanAndDev meanAndDev) {
      categorySet.add(meanAndDev.mean / 1000000, meanAndDev.dev / 1000000, category, clusterSize);
   }

   public void save(String filename) throws IOException {
      JFreeChart chart = createChart();
      ChartUtilities.saveChartAsPNG(new File(filename), chart, width, height);
   }

   protected abstract JFreeChart createChart();
}
