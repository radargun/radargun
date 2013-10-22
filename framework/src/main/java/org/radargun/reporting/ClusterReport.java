package org.radargun.reporting;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.jfree.data.statistics.DefaultStatisticalCategoryDataset;
import org.jfree.data.statistics.MeanAndStandardDeviation;

/**
 * @author Mircea.Markus@jboss.com
 */
public class ClusterReport extends AbstractClusterReport {

   private DefaultStatisticalCategoryDataset categorySet = new DefaultStatisticalCategoryDataset();

   public void addCategory(String rowKey, int columnKey, double mean, double dev) {
      this.categorySet.add(mean, dev, rowKey, Integer.valueOf(columnKey));
   }
   
   public DefaultStatisticalCategoryDataset getCategorySet() {
      return categorySet;
   }
   
   public void sort() {
      SortedMap<Comparable, SortedMap<Comparable, MeanAndStandardDeviation>> raw = new TreeMap<Comparable, SortedMap<Comparable, MeanAndStandardDeviation>>();
      for (int i = 0; i < categorySet.getRowCount(); i++) {
         Comparable row = categorySet.getRowKey(i);
         SortedMap<Comparable, MeanAndStandardDeviation> rowData = new TreeMap<Comparable, MeanAndStandardDeviation>();
         for (int j = 0; j < categorySet.getColumnCount(); j++) {
            Comparable column = categorySet.getColumnKey(j);
            Number mean = categorySet.getMeanValue(i, j);
            Number stddev = categorySet.getStdDevValue(i, j);
            rowData.put(column, new MeanAndStandardDeviation(mean, stddev));
         }
         raw.put(row, rowData);
      }

      categorySet.clear();
      for (Comparable row : raw.keySet()) {
         Map<Comparable, MeanAndStandardDeviation> rowData = raw.get(row);
         for (Comparable column : rowData.keySet()) {
            MeanAndStandardDeviation md = rowData.get(column);
            categorySet.add(md.getMean(), md.getStandardDeviation(), row, column);
         }
      }
   }
}
