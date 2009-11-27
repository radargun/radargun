package org.cachebench.reporting;

import java.io.IOException;

/**
 * Chart generator interface
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 */
public interface ChartGen
{

   void setReportDirectory(String reportDirectory);

   public void addToReportFilter(String productName, String productConfig);

   void generateChart() throws IOException;

   void setOutputDir(String outDir);

   public void setFileNamePrefix(String fileNamePrefix);
}
