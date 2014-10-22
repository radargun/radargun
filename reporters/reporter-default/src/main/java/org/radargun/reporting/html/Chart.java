package org.radargun.reporting.html;

import java.awt.*;
import java.io.File;
import java.io.IOException;

import org.jfree.chart.ChartColor;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;

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
