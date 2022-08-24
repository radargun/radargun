package org.radargun.stages.monitor;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.test.StressorsManager;
import org.radargun.stages.test.TestStage;
import org.radargun.traits.Clustered;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.JmxConnectionProvider;

/**
 * Stage to stop running the stressor when nodes changed during a test.
 *
 * Should be added after <service-start />
 */
@Stage(doc = "Periodically check the cluster size.")
public class PeriodicClusterSplitVerifyStage extends PeriodicStage {
   protected Log log = LogFactory.getLog(getClass());
   protected static final String CLEANUP = PeriodicClusterSplitVerifyStage.class.getSimpleName() + "_CLEANUP";
   protected static final String FUTURE = PeriodicClusterSplitVerifyStage.class.getSimpleName() + "_FUTURE";

   @Property(doc = "Location on disk where the heap dumps should be stored.", optional = false)
   protected String dir;

   @Property(doc = "If set it only prints objects which have active references and discards the ones that are ready to be garbage collected.")
   protected boolean live = false;

   @Property(doc = "When the cluster size was detected, if a heap dump should be generated.")
   protected boolean generateHeapDump;

   @InjectTrait
   private Clustered clustered;

   @InjectTrait
   private JmxConnectionProvider jmxConnectionProvider;

   @Override
   public PeriodicTask createTask() {
      return new ClusterSplitVerifyTask();
   }

   @Override
   public String getFutureKey() {
      return FUTURE;
   }

   @Override
   public String getCleanupKey() {
      return CLEANUP;
   }

   private class ClusterSplitVerifyTask implements PeriodicTask {

      private boolean splitDetected;

      @Override
      public void start() {
         int clusterSizeAtTestStart = clustered.getMembers().size();
         int membershipHistorySizeAtStart = clustered.getMembershipHistory().size();
         workerState.put(ClusterSplitVerifyStage.CLUSTER_SIZE_AT_START, clusterSizeAtTestStart);
         workerState.put(ClusterSplitVerifyStage.MEMBERSHIP_HISTORY_SIZE_AT_START, membershipHistorySizeAtStart);
      }

      @Override
      public void stop() {
      }

      @Override
      public void run() {
         if (!splitDetected) {
            int clusterSizeAtTestStart = (int) workerState.get(ClusterSplitVerifyStage.CLUSTER_SIZE_AT_START);
            int membershipHistorySizeAtStart = (int) workerState.get(ClusterSplitVerifyStage.MEMBERSHIP_HISTORY_SIZE_AT_START);
            for (int i = membershipHistorySizeAtStart; i < clustered.getMembershipHistory().size(); i++) {
               int clusterSizeDuringTest = clustered.getMembershipHistory().get(i).members.size();
               if (clusterSizeDuringTest != clusterSizeAtTestStart) {
                  if (generateHeapDump) {
                     HeapDumpTask heapDumpTask = new HeapDumpTask(jmxConnectionProvider, dir, workerState.getConfigName(), workerState.getWorkerIndex(), live);
                     heapDumpTask.start();
                     heapDumpTask.run();
                     heapDumpTask.stop();
                  }

                  StressorsManager stressorsManager = (StressorsManager) workerState.get(TestStage.STRESSORS_MANAGER);
                  if (stressorsManager == null) {
                     log.warn("stressorsManager is null. Are you using LoadStage? Try BasicOperationsTestStage");
                  } else {
                     log.info("Cluster size at the beginning of the test was " + clusterSizeAtTestStart + " but changed to " + clusterSizeDuringTest +
                           " during the test! Perhaps a split occured, or a new node joined?. Stopping the stressors.");
                     stressorsManager.getStressors().forEach(s -> s.setContinueRunning(false));
                  }
                  splitDetected = true;
               }
            }
         }
      }
   }
}
