package org.radargun.stages.cache.background;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.state.ServiceListenerAdapter;
import org.radargun.state.SlaveState;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.OperationThroughput;

/**
 * Coordinator for collecting statistics from background stressor threads started by {@link org.radargun.stages.cache.background.BackgroundOpsManager}.
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class BackgroundStatisticsManager extends ServiceListenerAdapter {

   public static final String CACHE_SIZE = "Cache size";
   private static final String PREFIX = "BackgroundStatistics.";
   private static final Log log = LogFactory.getLog(BackgroundStatisticsManager.class);

   private BackgroundOpsManager backgroundOpsManager;
   private long statsIterationDuration;
   private List<IterationStats> stats;

   private ScheduledFuture statsTask;
   private SizeThread sizeThread;
   private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

   private BackgroundStatisticsManager() {}

   private BackgroundStatisticsManager(BackgroundOpsManager backgroundOpsManager, long statsIterationDuration) {
      this.backgroundOpsManager = backgroundOpsManager;
      this.statsIterationDuration = statsIterationDuration;
   }

   /**
    * Returns {@link org.radargun.stages.cache.background.BackgroundStatisticsManager} instance. Creates {@link org.radargun.stages.cache.background.BackgroundOpsManager}
    * internally, if not found in slave state.
    */
   public static BackgroundStatisticsManager getOrCreateInstance(SlaveState slaveState, String name, long statsIterationDuration) {
      BackgroundStatisticsManager statisticsManager = getInstance(slaveState, name);
      if (statisticsManager == null) {
         statisticsManager = new BackgroundStatisticsManager(BackgroundOpsManager.getOrCreateInstance(slaveState, name), statsIterationDuration);
         slaveState.put(PREFIX + name, statisticsManager);
      }
      return statisticsManager;
   }

   public static BackgroundStatisticsManager getInstance(SlaveState slaveState, String name) {
      return (BackgroundStatisticsManager) slaveState.get(PREFIX + name);
   }

   public synchronized List<IterationStats> getStats() {
      List<IterationStats> statsToReturn = stats;
      stats = null;
      return statsToReturn;
   }

   public synchronized void startStats() {
      if (stats == null) {
         stats = new ArrayList<>();
      }
      if (sizeThread == null) {
         sizeThread = new SizeThread();
         sizeThread.start();
      }
      if (statsTask == null) {
         statsTask = executor.scheduleAtFixedRate(new StatsTask(), 0, statsIterationDuration, TimeUnit.MILLISECONDS);
      }
   }

   public synchronized void stopStats() {
      if (statsTask != null) {
         statsTask.cancel(true);
         statsTask = null;
      }
      if (sizeThread != null) {
         log.debug("Interrupting size thread");
         sizeThread.interrupt();
         try {
            sizeThread.join();
         } catch (InterruptedException e) {
            log.error("Interrupted while waiting for stat thread to end.");
         }
         sizeThread = null;
      }
   }

   @Override
   public void serviceDestroyed() {
      stopStats();
   }

   private class StatsTask implements Runnable {
      public StatsTask() {
         gatherStats(); // throw away first stats
      }

      public void run() {
         stats.add(gatherStats());
      }

      private IterationStats gatherStats() {
         Stressor[] threads = backgroundOpsManager.getStressorThreads();
         List<Statistics> stats;
         if (threads == null) {
            stats = Collections.EMPTY_LIST;
         } else {
            stats = new ArrayList<Statistics>(threads.length);
            for (int i = 0; i < threads.length; i++) {
               if (threads[i] != null) {
                  stats.add(threads[i].getStatsSnapshot(true));
               }
            }
         }
         Timeline timeline = backgroundOpsManager.getSlaveState().getTimeline();
         long now = System.currentTimeMillis();
         long cacheSize = sizeThread.getAndResetSize();
         timeline.addValue(CACHE_SIZE, new Timeline.Value(now, cacheSize));
         if (stats.isEmpty()) {
            // add zero for all operations we've already reported
            for (String valueCategory : timeline.getValueCategories()) {
               if (valueCategory.endsWith(" Throughput")) {
                  timeline.addValue(valueCategory, new Timeline.Value(now, 0));
               }
            }
         } else {
            Statistics aggregated = stats.get(0).copy();
            for (int i = 1; i < stats.size(); ++i) {
               aggregated.merge(stats.get(i));
            }
            for (Map.Entry<String, OperationStats> entry : aggregated.getOperationsStats().entrySet()) {
               OperationThroughput throughput = entry.getValue().getRepresentation(OperationThroughput.class, stats.size(), TimeUnit.MILLISECONDS.toNanos(aggregated.getEnd() - aggregated.getBegin()));
               if (throughput != null && (throughput.actual != 0 || timeline.getValues(entry.getKey() + " Throughput") != null)) {
                  timeline.addValue(entry.getKey() + " Throughput", new Timeline.Value(now, throughput.actual));
               }
            }
         }
         log.trace(String.format("Adding iteration %d: %s.", BackgroundStatisticsManager.this.stats.size(), stats));
         return new IterationStats(stats, cacheSize);
      }
   }

   /**
    *
    * Used for fetching cache size. If the size can't be fetched during one stat iteration, value 0
    * will be used.
    *
    */
   private class SizeThread extends Thread {
      private boolean getSize = true;
      private long size = -1;

      @Override
      public void run() {
         try {
            while (!isInterrupted()) {
               synchronized (this) {
                  while (!getSize) {
                     wait(100);
                  }
                  getSize = false;
               }
               if (backgroundOpsManager.getCacheInfo() != null && backgroundOpsManager.getLifecycle().isRunning()) {
                  size = backgroundOpsManager.getCacheInfo().getOwnedSize();
               } else {
                  size = 0;
               }
            }
         } catch (InterruptedException e) {
            log.trace("SizeThread interrupted.");
         }
      }

      public synchronized long getAndResetSize() {
         long rSize = size;
         size = -1;
         getSize = true;
         notify();
         return rSize;
      }
   }

   public static class IterationStats implements Serializable {
      public final List<Statistics> statistics;
      public final long cacheSize;

      private IterationStats(List<Statistics> statistics, long cacheSize) {
         this.statistics = statistics;
         this.cacheSize = cacheSize;
      }
   }

}
