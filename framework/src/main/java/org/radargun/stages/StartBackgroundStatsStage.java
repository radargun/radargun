package org.radargun.stages;

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.stressors.BackgroundStats;

/**
 * 
 * Create BackgroundStats and store them to SlaveState. Optionally start stressor or stat threads.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 */
public class StartBackgroundStatsStage extends AbstractDistStage {

   private int puts = 1;
   private int gets = 2;
   private int numEntries = 1024;
   private int entrySize = 1024;
   private int numThreads = 10;
   private int transactionSize = -1;
   private long delayBetweenRequests;
   private long statsIterationDuration = 5000;
   private boolean waitUntilLoaded = true;
   protected List<Integer> loadDataForDeadSlaves;
   private boolean startStressors = false;
   private boolean startStats = false;

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         BackgroundStats bgStats = (BackgroundStats) slaveState.get(BackgroundStats.NAME);
         if (bgStats == null) {
            bgStats = new BackgroundStats(puts, gets, numEntries, entrySize, numThreads, slaveState,
                  delayBetweenRequests, getActiveSlaveCount(), getSlaveIndex(), statsIterationDuration,
                  transactionSize, loadDataForDeadSlaves);
            slaveState.put(BackgroundStats.NAME, bgStats);
         }
         if (startStressors) {
            log.info("Starting stressor threads");
            if (slaveState.getCacheWrapper() != null) {
               bgStats.startStressors();
               if (waitUntilLoaded) {
                  log.info("Waiting until all stressor threads load data");
                  bgStats.waitUntilLoaded();
               }
               bgStats.setLoaded();
            }
         }
         if (startStats) {
            log.info("Starting stat thread");
            bgStats.startStats();
         }
         return ack;
      } catch (Exception e) {
         log.error("Error while starting background stats");
         ack.setError(true);
         ack.setRemoteException(e);
         return ack;
      }
   }

   public void setPuts(int puts) {
      this.puts = puts;
   }

   public void setGets(int gets) {
      this.gets = gets;
   }

   public void setNumEntries(int numEntries) {
      this.numEntries = numEntries;
   }

   public void setEntrySize(int entrySize) {
      this.entrySize = entrySize;
   }

   public void setNumThreads(int numThreads) {
      this.numThreads = numThreads;
   }

   public void setDelayBetweenRequests(long delayBetweenRequests) {
      this.delayBetweenRequests = delayBetweenRequests;
   }

   public void setStatsIterationDuration(long statsIterationDuration) {
      this.statsIterationDuration = statsIterationDuration;
   }

   public void setTransactionSize(int transactionSize) {
      this.transactionSize = transactionSize;
   }

   public void setWaitUntilLoaded(boolean waitUntilLoaded) {
      this.waitUntilLoaded = waitUntilLoaded;
   }

   public void setLoadDataForDeadSlaves(String slaves) {
      this.loadDataForDeadSlaves = new ArrayList<Integer>();
      for (String slave : slaves.split(",")) {
         this.loadDataForDeadSlaves.add(Integer.valueOf(slave));
      }
   }

   public void setStartStats(boolean startStats) {
      this.startStats = startStats;
   }

   public void setStartStressors(boolean startStressors) {
      this.startStressors = startStressors;
   }

   @Override
   public String toString() {
      return "StartBackgroundStatsStage {puts=" + puts + ", gets=" + gets + ", startStats=" + startStats
            + ", startStressors=" + startStressors + ", numEntries=" + numEntries + ", entrySize=" + entrySize
            + ", numThreads=" + numThreads + ", transactionSize=" + transactionSize + ", delayBetweenRequests="
            + delayBetweenRequests + ", statsIterationDuration=" + statsIterationDuration + ", waitUntilLoaded="
            + waitUntilLoaded + ", loadDataForDeadSlaves=" + loadDataForDeadSlaves + ", " + super.toString();
   }

}
