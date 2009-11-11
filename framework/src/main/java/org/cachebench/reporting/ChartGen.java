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

   void setFileNamePrefix(String fnPrefix);

   void generateChart() throws IOException;

   void setOutputDir(String outDir);
}
