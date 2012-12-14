package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stressors.BackgroundStats;

import java.util.List;

/**
 * 
 * Create BackgroundStats and store them to SlaveState. Optionally start stressor or stat threads.
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
@Stage(doc = "Start stressor threads or statistics threads.")
public class StartBackgroundStatsStage extends AbstractDistStage {

   @Property(doc = "Ratio of PUT requests. Default is 1.")
   private int puts = 1;

   @Property(doc = "Ratio of GET requests. Default is 2.")
   private int gets = 2;

   @Property(doc = "Ratio of REMOVE requests. Default is 0.")
   private int removes = 0;

   @Property(doc = "Amount of entries (key-value pairs) inserted into the cache. Default is 1024.")
   private int numEntries = 1024;

   @Property(doc = "Size of value used in the entry. Default is 1024 bytes.")
   private int entrySize = 1024;

   @Property(doc = "Number of stressor threads. Default is 10.")
   private int numThreads = 10;

   @Property(doc = "Amount of request wrapped into single transaction. By default transactions are not used (explicitely).")
   private int transactionSize = -1;

   @Property(converter = TimeConverter.class, doc = "Time between consecutive requests of one stressor thread. Default is 0.")
   private long delayBetweenRequests = 0;

   @Property(converter = TimeConverter.class, doc = "Delay between statistics snapshots. Default is 5 seconds.")
   private long statsIterationDuration = 5000;

   @Property(doc = "Specifies whether the stage should wait until the entries are loaded by stressor threads. Default is true.")
   private boolean waitUntilLoaded = true;

   @Property(doc = "List of slaves whose data should be loaded by other threads because these slaves are not alive. Default is empty.")
   protected List<Integer> loadDataForDeadSlaves;

   @Property(doc = "Should the stressor threads be started? Default is false.")
   private boolean startStressors = false;

   @Property(doc = "Should the statistic thread be started? Default is false.")
   private boolean startStats = false;

   @Property(doc = "Bucket where the entries should be inserted. Default is ")
   private String bucketId;

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         BackgroundStats bgStats = (BackgroundStats) slaveState.get(BackgroundStats.NAME);
         if (bgStats == null) {
            bgStats = new BackgroundStats(puts, gets, removes, numEntries, entrySize, bucketId, numThreads, slaveState,
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
         log.error("Error while starting background stats", e);
         ack.setError(true);
         ack.setRemoteException(e);
         return ack;
      }
   }
}
