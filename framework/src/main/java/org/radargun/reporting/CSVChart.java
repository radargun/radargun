package org.radargun.reporting;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Draws a generic multi-series line chart based on CSV data.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 */
public class CSVChart {

   public static final String NULL = "null";
   public static final String SEPARATOR = ";";

   public static void writeCSVAsChart(String title, String categoryAxisLabel, String valueAxisLabel, String csvFile,
         String csvSeparator, String domainColumn, List<String> valueColumns, int width, int height, String outputPath)
         throws Exception {
      XYDataset dataset = createDataset(csvFile, csvSeparator, domainColumn, valueColumns);
      JFreeChart chart = ChartFactory.createXYLineChart(title, categoryAxisLabel, valueAxisLabel, dataset,
            PlotOrientation.VERTICAL, true, false, false);
      XYPlot plot = (XYPlot) chart.getPlot();
      NumberAxis dAxis = (NumberAxis) plot.getDomainAxis();
      dAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
      FileOutputStream output = new FileOutputStream(outputPath);
      ChartUtilities.writeChartAsPNG(output, chart, width, height);
      output.close();
   }

   private static XYDataset createDataset(String csvFile, String csvSeparator, String domainColumn,
         List<String> valueColumns) throws Exception {
      XYSeriesCollection dataset = new XYSeriesCollection();
      String[][] csvData = parseCSVData(csvFile, csvSeparator);
      int domainIdx = findColIdx(csvData, domainColumn);

      for (int i = 0; i < valueColumns.size(); i++) {
         int valIdx = findColIdx(csvData, valueColumns.get(i));
         XYSeries series = new XYSeries(valueColumns.get(i));
         for (int j = 1; j < csvData.length; j++) {
            String[] row = csvData[j];
            String yStr = row[valIdx];
            if (!row[domainIdx].equals(NULL) && yStr != null && !yStr.isEmpty() && !yStr.equals(NULL)) {
               series.add(new Double(row[domainIdx]), new Double(yStr));
            }
         }
         dataset.addSeries(series);
      }
      return dataset;
   }

   private static int findColIdx(String[][] relation, String column) {
      if (relation == null) {
         return -1;
      }
      String[] columns = relation[0];
      for (int i = 0; i < columns.length; i++) {
         if (columns[i].equals(column)) {
            return i;
         }
      }
      throw new IllegalArgumentException("Column " + column + " not found.");
   }

   private static String[][] parseCSVData(String csvFile, String separator) throws Exception {
      List<String[]> result = new ArrayList<String[]>();
      BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), "UTF-8"));
      String line = null;
      while ((line = r.readLine()) != null) {
         result.add(line.split(separator));
      }
      r.close();
      return result.toArray(new String[result.size()][]);
   }
}