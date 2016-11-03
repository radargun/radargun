package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.traits.Clustered;
import org.radargun.traits.InjectTrait;

/**
 * This stage needs to be defined twice:
 *
 *     <cluster-split-verify />
 *     ... other stages ...
 *     <cluster-split-verify verify="true" />
 *
 * First run will record the cluster size, and the second will verify that that the size didn't change
 * during the time.
 */
@Stage(doc = "Verifies that there weren't any changes to the cluster size during testing.")
public class ClusterSplitVerifyStage extends AbstractDistStage {

   public static final String CLUSTER_SIZE_AT_START = "CLUSTER_SIZE_AT_START";
   public static final String MEMBERSHIP_HISTORY_SIZE_AT_START = "MEMBERSHIP_HISTORY_SIZE_AT_START";

   @Property(doc = "Set to true in 2nd definition of the stage in benchmark to verify that no splits occurred. Default is false.")
   private boolean verify = false;

   @InjectTrait
   private Clustered clustered;

   @Override
   public DistStageAck executeOnSlave() {
      if (!verify) {
         int clusterSizeAtTestStart = clustered.getMembers().size();
         int membershipHistorySizeAtStart = clustered.getMembershipHistory().size();
         slaveState.put(CLUSTER_SIZE_AT_START, clusterSizeAtTestStart);
         slaveState.put(MEMBERSHIP_HISTORY_SIZE_AT_START, membershipHistorySizeAtStart);
      } else {
         int clusterSizeAtTestStart = (int) slaveState.get(CLUSTER_SIZE_AT_START);
         int membershipHistorySizeAtStart = (int) slaveState.get(MEMBERSHIP_HISTORY_SIZE_AT_START);
         for (int i = membershipHistorySizeAtStart; i < clustered.getMembershipHistory().size(); i++) {
            int clusterSizeDuringTest = clustered.getMembershipHistory().get(i).members.size();
            if (clusterSizeDuringTest != clusterSizeAtTestStart) {
               return errorResponse("Cluster size at the beginning of the test was " + clusterSizeAtTestStart + " but changed to " + clusterSizeDuringTest +
                  " during the test! Perhaps a split occured, or a new node joined?");
            }
         }
      }
      return successfulResponse();
   }

}
