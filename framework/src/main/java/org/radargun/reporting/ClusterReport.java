package org.radargun.reporting;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public interface ClusterReport {

   void setReportFile(String reportDir, String fileName);

   void init(String xLabels, String yLabels, String title, String subtitle);

   void addCategory(String productName, int clusterSize, Number value);

   void generate() throws Exception;
}
