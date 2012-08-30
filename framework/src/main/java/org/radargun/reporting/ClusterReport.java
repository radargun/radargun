package org.radargun.reporting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.jfree.data.category.DefaultCategoryDataset;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class ClusterReport {

   private DefaultCategoryDataset categorySet = new DefaultCategoryDataset();
   private String xLabel;
   private String yLabel;
   private String title;
   private String subtitle;
   private List<String> notes = new ArrayList<String>();
      
   public void init(String xLabel, String yLabel, String title, String subtitle) {
      this.xLabel = xLabel;
      this.yLabel = yLabel;
      this.title = title;
      this.subtitle = subtitle;
   }

   public void addCategory(String rowKey, int columnKey, Number value) {
      this.categorySet.addValue(value, rowKey, columnKey);
   }
   
   public void addNote(String note) {
      notes.add(note);
   }

   public String getTitle() {
      return title;
   }

   public String getSubtitle() {
      return subtitle;
   }

   public String getXLabel() {
      return xLabel;
   }

   public String getYLabel() {
      return yLabel;
   }
   
   public List<String> getNotes() {
      return notes;
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
