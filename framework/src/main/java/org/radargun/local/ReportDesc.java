package org.radargun.local;

import java.util.ArrayList;
import java.util.List;

/**
 * // TODO: Mircea - Document this!
 *
 * @author
 */
public class ReportDesc {

   private String reportName;
   private List<ReportItem> items = new ArrayList<ReportItem>();
   private boolean includeAll;


   public void setReportName(String reportName) {
      this.reportName = reportName;
   }

   public void addReportItem(String productName, String configuration) {
      ReportItem reportItem = new ReportItem(productName, configuration);
      items.add(reportItem);
   }

   public void setIncludeAll(boolean includeAll) {
      this.includeAll = includeAll;
   }

   public boolean isIncludeAll() {
      return includeAll;
   }

   public void updateData(String product, String config, long readsPerSec, long noReads, long writesPerSec, long noWrites) {
      if (includeAll) {
         ReportItem item = new ReportItem(product, config);
         updateItem(readsPerSec, noReads, writesPerSec, noWrites, item);
         items.add(item);
      } else {
         for (ReportItem item : items) {
            if (item.matches(product, config)) {
               updateItem(readsPerSec, noReads, writesPerSec, noWrites, item);
            }
         }
      }
   }

   private void updateItem(long readsPerSec, long noReads, long writesPerSec, long noWrites, ReportItem item) {
      item.setReadsPerSec(readsPerSec);
      item.setNoReads(noReads);
      item.setWritesPerSec(writesPerSec);
      item.setNoWrites(noWrites);
   }

   public String getReportName() {
      return reportName;
   }

   public List<ReportItem> getItems() {
      return items;
   }

   public void addReportItems(List<ReportItem> all) {
      items.addAll(all);
   }
}
