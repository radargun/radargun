package org.radargun.reporting.html;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.jfree.chart.ChartColor;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.Dataset;
import org.jfree.data.xy.XYDataset;

/**
 * Base for charts
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class Chart {
   protected static final Paint[] DEFAULT_PAINTS = ChartColor.createDefaultPaintArray();

   protected int width;
   protected int height;

   public void save(String filename) throws IOException {
      JFreeChart chart = createChart();
      ChartUtilities.saveChartAsPNG(new File(filename), chart, width, height);
      storeDataSet(chart, filename);
   }

   private void storeDataSet(JFreeChart chart, String filename) {
      java.util.List<String> csv = new ArrayList<>();
      if (chart.getPlot() instanceof XYPlot) {
         Dataset dataset = chart.getXYPlot().getDataset();
         XYDataset xyDataset = (XYDataset) dataset;
         int seriesCount = xyDataset.getSeriesCount();
         for (int i = 0; i < seriesCount; i++) {
            int itemCount = xyDataset.getItemCount(i);
            for (int j = 0; j < itemCount; j++) {
               Comparable key = xyDataset.getSeriesKey(i);
               Number x = xyDataset.getX(i, j);
               Number y = xyDataset.getY(i, j);
               csv.add(String.format("%s, %s, %s", key, x, y));
            }
         }

      } else if (chart.getPlot() instanceof CategoryPlot) {
         Dataset dataset = chart.getCategoryPlot().getDataset();
         CategoryDataset categoryDataset = (CategoryDataset) dataset;
         int columnCount = categoryDataset.getColumnCount();
         int rowCount = categoryDataset.getRowCount();
         for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < columnCount; j++) {
               Comparable key = categoryDataset.getRowKey(i);
               Number n = categoryDataset.getValue(i, j);
               csv.add(String.format("%s, %s", key, n));
            }
         }
      } else {
         throw new IllegalStateException("Unknown dataset");
      }
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename + ".csv"));) {
         for (String line : csv) {
            writer.append(line);
            writer.newLine();
         }
      } catch (IOException e) {
         throw new IllegalStateException("Cannot write dataset", e);
      }
   }

   public Chart setWidth(int width) {
      this.width = width;
      return this;
   }

   public Chart setHeight(int height) {
      this.height = height;
      return this;
   }

   protected abstract JFreeChart createChart();
}
