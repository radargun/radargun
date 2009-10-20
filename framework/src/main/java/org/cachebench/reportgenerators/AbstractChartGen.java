package org.cachebench.reportgenerators;

import org.jfree.chart.title.TextTitle;

import java.util.Date;

public abstract class AbstractChartGen implements ChartGen
{
   protected String reportDirectory;
   protected String filenamePrefix;
   protected static final String MU = "\u00B5";//((char) 0xC2B5) + ""; 

   public void setReportDirectory(String reportDirectory)
   {
      this.reportDirectory = reportDirectory;
   }


   public void setFileNamePrefix(String fnPrefix)
   {
      this.filenamePrefix = fnPrefix;
   }

   protected TextTitle getSubtitle()
   {
      return new TextTitle("Generated on " + new Date() + " by The CacheBenchFwk\nJDK: " +
            System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.version") + ", " +
            System.getProperty("java.vm.vendor") + ") OS: " + System.getProperty("os.name") + " (" +
            System.getProperty("os.version") + ", " + System.getProperty("os.arch") + ")");
   }
}
