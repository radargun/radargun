package org.radargun.reporting;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.jfree.data.category.DefaultCategoryDataset;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class ClusterReport extends AbstractClusterReport {

   private DefaultCategoryDataset categorySet = new DefaultCategoryDataset();
   public void addCategory(String rowKey, int columnKey, Number value) {
      this.categorySet.addValue(value, rowKey, columnKey);
   }
   
   public DefaultCategoryDataset getCategorySet() {
      return categorySet;
   }
   
   public void sort() {
      SortedMap<Comparable, SortedMap<Comparable, Number>> raw = new TreeMap<Comparable, SortedMap<Comparable, Number>>();
      for (int i = 0; i < categorySet.getRowCount(); i++) {
         Comparable row = categorySet.getRowKey(i);
         SortedMap<Comparable, Number> rowData = new TreeMap<Comparable, Number>();
         for (int j = 0; j < categorySet.getColumnCount(); j++) {
            Comparable column = categorySet.getColumnKey(j);
            Number value = categorySet.getValue(i, j);
            rowData.put(column, value);
         }
         raw.put(row, rowData);
      }

      categorySet.clear();
      for (Comparable row : raw.keySet()) {
         Map<Comparable, Number> rowData = raw.get(row);
         for (Comparable column : rowData.keySet()) {
            categorySet.addValue(rowData.get(column), row, column);
         }
      }
   }
}
