package org.radargun.reporting;

import java.io.IOException;

/**
 * Chart generator interface
 *
 * @author Manik Surtani
 */
public interface ChartGen {

   void setReportDirectory(String reportDirectory);

   void addToReportFilter(String productName, String productConfig);

   void generateChart() throws IOException;

   void setOutputDir(String outDir);

   void setFileNamePrefix(String fileNamePrefix);
}
