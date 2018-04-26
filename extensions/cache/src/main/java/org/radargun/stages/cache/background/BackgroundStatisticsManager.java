package org.radargun.stages.cache.background;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.state.ServiceListener;
import org.radargun.state.SlaveState;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.OperationThroughput;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * Coordinator for collecting statistics from background stressor threads started by
 * {@link org.radargun.stages.cache.background.BackgroundOpsManager}.
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class BackgroundStatisticsManager implements ServiceListener {

   public static final String CACHE_SIZE = "Cache size";
   private static final String PREFIX = "BackgroundStatistics.";
   private static final Log log = LogFactory.getLog(BackgroundStatisticsManager.class);

   private BackgroundOpsManager backgroundOpsManager;
   private long statsIterationDuration;
   private List<IterationStats> stats;

   private ScheduledFuture statsTask;
   private SizeThread sizeThread;
   private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

   private BackgroundStatisticsManager() {
   }

   private BackgroundStatisticsManager(BackgroundOpsManager backgroundOpsManager, long statsIterationDuration) {
      this.backgroundOpsManager = backgroundOpsManager;
      this.statsIterationDuration = statsIterationDuration;
   }

   /**
    * Returns {@link org.radargun.stages.cache.background.BackgroundStatisticsManager} instance.
    * Creates {@link org.radargun.stages.cache.background.BackgroundOpsManager} internally, if not
    * found in slave state.
    */
   public static BackgroundStatisticsManager getOrCreateInstance(SlaveState slaveState, String name,
                                                                 long statsIterationDuration) {
      BackgroundStatisticsManager statisticsManager = getInstance(slaveState, name);
      if (statisticsManager == null) {
         statisticsManager = new BackgroundStatisticsManager(BackgroundOpsManager.getOrCreateInstance(slaveState, name),
               statsIterationDuration);
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

         if (statsTask.cancel(true)) {
            statsTask = null;
         } else {
            log.error("Statistics task was not cancelled");
         }
         Utils.shutdownAndWait(executor);
         if (!executor.isTerminated()) {
            log.warn("Failed to terminate statistics executor service.");
         }

         this.statsTask = null;
         this.executor = Executors.newScheduledThreadPool(1);
      }
      if (sizeThread != null) {
         log.debug("Interrupting size thread");
         sizeThread.interrupt();
         try {
            sizeThread.join();
         } catch (InterruptedException e) {
            log.error("Interrupted while waiting for stat thread to end.");
            sizeThread.interrupt();
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
         Stressor[] threads = backgroundOpsManager.getThreadManager().getStressorThreads();
         List<Statistics> stats;
         if (threads == null) {
            stats = Collections.EMPTY_LIST;
         } else {
            stats = Arrays.asList(threads).stream().filter(t -> t != null).map(t -> t.getStatsSnapshot(true))
                  .collect(Collectors.toList());
         }
         Timeline timeline = backgroundOpsManager.getSlaveState().getTimeline();
         long now = TimeService.currentTimeMillis();
         long cacheSize = sizeThread.getAndResetSize();
         timeline.addValue(Timeline.Category.customCategory(CACHE_SIZE), new Timeline.Value(now, cacheSize));
         if (stats.isEmpty()) {
            // add zero for all operations we've already reported
            for (Timeline.Category valueCategory : timeline.getValueCategories()) {
               if (valueCategory.getName().endsWith(" Throughput")) {
                  timeline.addValue(valueCategory, new Timeline.Value(now, 0));
               }
            }
         } else {
            Statistics aggregated = stats.stream().reduce(Statistics.MERGE)
                  .orElseThrow(() -> new IllegalStateException("No statistics!"));
            for (String operation : aggregated.getOperations()) {
               OperationThroughput throughput = aggregated.getRepresentation(operation, OperationThroughput.class);
               Timeline.Category category = Timeline.Category.customCategory(operation + " Throughput");
               if (throughput != null && (throughput.gross != 0 || timeline.getValues(category) != null)) {
                  timeline.addValue(category, new Timeline.Value(now, throughput.gross));
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
            interrupt();
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
