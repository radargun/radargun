package org.cachebench.reportgenerators.reportcentralizer;

import java.util.*;

/**
 * Gathers the report data from all distributions that corespond to the same benchmark.
 *
 * @author Mircea.Markus@jboss.com
 */
public class ConfigurationData {

   private String configurationName;
   private Set<String> distributions = new HashSet<String>();
   private Map<String, List<ReportData>> distriution2ReportDataMap = new HashMap<String, List<ReportData>>();

   public ConfigurationData(String configurationName) {
      this.configurationName = configurationName;
   }

   public void addIfNeeded(ReportData reportData) {
      if (reportData.getConfiguration().equals(configurationName)) {
         String distribution = reportData.getDistribution();
         distributions.add(distribution);
         List<ReportData> dataList = distriution2ReportDataMap.get(distribution);
         if (dataList == null) {
            dataList = new ArrayList<ReportData>();
            distriution2ReportDataMap.put(distribution, dataList);
         }
         dataList.add(reportData);
      }
   }

   public Map<String, List<ReportData>> getDistriution2ReportDataMap() {
      for (List<ReportData> datas : distriution2ReportDataMap.values()) {
         Collections.sort(datas);
      }
      return distriution2ReportDataMap;
   }

   public String getConfigurationName() {
      return configurationName;
   }

   public int[] getClusterSizes() {
      String aDistribution = distributions.iterator().next();
      List<ReportData> reportDatas = getDistriution2ReportDataMap().get(aDistribution);
      int[] result = new int[reportDatas.size()];
      for (int i = 0; i < reportDatas.size(); i++) {
         result[i] = reportDatas.get(i).getClusterSize();
      }
      return result;
   }
}
