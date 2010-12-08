package org.radargun.local;

import org.radargun.reporting.AbstractChartGen;
import org.radargun.utils.Utils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.CategoryItemLabelGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer3D;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.*;
import java.io.File;
import java.text.NumberFormat;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class LocalChartGenerator {
   
   private ReportDesc reportDesc;
   private DefaultCategoryDataset getData = new DefaultCategoryDataset();
   private DefaultCategoryDataset putData = new DefaultCategoryDataset();

   public LocalChartGenerator(ReportDesc reportDesc) {
      this.reportDesc = reportDesc;
   }

   public void generateChart(String dir) throws Exception {
      long nrReads = 0;
      long nrWrites = 0;
      for (ReportItem item : reportDesc.getItems()) {
         getData.addValue(item.getReadsPerSec(), item.description(), "GET");
         nrReads += item.getNoReads();
         putData.addValue(item.getWritesPerSec(), item.description(), "PUT");
         nrWrites += item.getNoWrites();
      }
      File localGetFile = new File(dir, "local_gets_" + reportDesc.getReportName() + ".png");
      Utils.backupFile(localGetFile);

      ChartUtilities.saveChartAsPNG(localGetFile, createChart("Report: Comparing Cache GET (READ) performance", getData, (int) (nrReads / reportDesc.getItems().size()), "(GETS)"), 800, 800);

      File localPutFile = new File(dir, "local_puts_" + reportDesc.getReportName() + ".png");
      Utils.backupFile(localPutFile);

      ChartUtilities.saveChartAsPNG(localPutFile, createChart("Report: Comparing Cache PUT (WRITE) performance", putData, (int) (nrWrites / reportDesc.getItems().size()), "(PUTS)"), 800, 800);
   }

   private JFreeChart createChart(String title, DefaultCategoryDataset data, int numOperations, String operation) {
      JFreeChart chart = ChartFactory.createBarChart3D(title, "Cache operations performed (approx): " + NumberFormat.getIntegerInstance().format(numOperations), "Operations/second" + operation, data, PlotOrientation.VERTICAL, true, true, false);
      BarRenderer3D renderer = (BarRenderer3D) chart.getCategoryPlot().getRenderer();
      renderer.setBaseItemLabelsVisible(true);

      final NumberFormat fmt = NumberFormat.getNumberInstance();
      fmt.setMaximumFractionDigits(0);
      fmt.setMinimumFractionDigits(0);

      renderer.setBaseItemLabelGenerator(new CategoryItemLabelGenerator() {

         public String generateRowLabel(CategoryDataset categoryDataset, int i) {
            return null;
         }

         public String generateColumnLabel(CategoryDataset categoryDataset, int i) {
            return null;
         }

         public String generateLabel(CategoryDataset categoryDataset, int product, int operation) {
            String retval;
            try {
               retval = fmt.format(categoryDataset.getValue(product, operation)) + " Ops/sec";
            }
            catch (Exception e) {
               e.printStackTrace();
               retval = e.toString();
            }
            return retval;
         }
      });

      chart.addSubtitle(AbstractChartGen.getSubtitle());

      chart.setBorderVisible(true);
      chart.setAntiAlias(true);
      chart.setTextAntiAlias(true);
      chart.setBackgroundPaint(new Color(0x61, 0x9e, 0xa1));
      return chart;
   }
}
