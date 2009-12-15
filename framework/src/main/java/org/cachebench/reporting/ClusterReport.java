package org.cachebench.reporting;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public interface ClusterReport {

   public void setReportFile(String reportDir, String fileName);

   public void init(String xLabels, String yLabels, String title, String subtitle);

   public void addCategory(String productName, int clusterSize, Number value);

   public void generate() throws Exception;
}
