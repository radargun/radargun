package org.radargun.sysmonitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.radargun.reporting.Timeline;
import org.radargun.utils.Utils;

public class RssMonitor extends AbstractMonitor {

   private static final String RSS_MEMORY_USAGE = "RSS Memory usage";
   private final Timeline timeline;

   public RssMonitor(Timeline timeline) {
      this.timeline = timeline;
   }

   @Override
   public void runMonitor() {
      Process process = null;
      try {
         process = createProcess();
         Long rssUsage = getRssUsageFrom(process);
         timeline.addValue(Timeline.Category.sysCategory(RSS_MEMORY_USAGE), new Timeline.Value(rssUsage));
      } finally {
         if (process != null)
            process.destroy();
      }
   }

   private Process createProcess() {
      try {
         return Runtime.getRuntime().exec("ps --no-headers -o rss " + Utils.getProcessID());
      } catch (IOException e) {
         throw new IllegalStateException("Error when starting the process.", e);
      }
   }

   private Long getRssUsageFrom(Process process) {
      Long rssUsage;
      try (InputStream inputStream = process.getInputStream();
         Reader inputStreamReader = new InputStreamReader(inputStream);
         BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
         rssUsage = Long.valueOf(bufferedReader.readLine());
      } catch (Exception e) {
         throw new IllegalStateException("Error in rss stats retrieval", e);
      }
      return rssUsage;
   }
}
