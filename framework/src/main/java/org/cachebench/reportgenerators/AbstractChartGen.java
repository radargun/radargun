package org.cachebench.reportgenerators;

import org.jfree.chart.title.TextTitle;

import java.io.File;
import java.io.IOException;
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

   protected void initOutputDirectories() {
      if (filenamePrefix != null && !filenamePrefix.trim().equals("")) {
         File f = new File(filenamePrefix);
         if (f.exists() && f.isDirectory() && !filenamePrefix.endsWith(File.separator))
            filenamePrefix += File.separator;
      }
   }

   protected File getChartFile(String defaultName) {
      File cf;
      if (filenamePrefix != null && !filenamePrefix.trim().equals("")) {
         if (filenamePrefix.endsWith(File.separator))
            cf = new File(filenamePrefix + defaultName);
         else
            cf = new File(filenamePrefix + "-" + defaultName);

      } else {
         cf = new File(defaultName);
      }

      if (cf.exists()) {
        // move old version of the file.
         cf.renameTo(new File(cf.getName() + "." + System.currentTimeMillis()));
         cf = getChartFile(defaultName);
      } else {
         // make any parent dirs if necessary
         cf.getParentFile().mkdirs();
      }
      return cf;
   }

   protected TextTitle getSubtitle()
   {
      return new TextTitle("Generated on " + new Date() + " by The CacheBenchFwk\nJDK: " +
            System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.version") + ", " +
            System.getProperty("java.vm.vendor") + ") OS: " + System.getProperty("os.name") + " (" +
            System.getProperty("os.version") + ", " + System.getProperty("os.arch") + ")");
   }

   protected abstract void readData() throws IOException;

   protected abstract void createCharts() throws IOException;

   public void generateChart() throws IOException {
      initOutputDirectories();
      readData();
      createCharts();
   }
}
