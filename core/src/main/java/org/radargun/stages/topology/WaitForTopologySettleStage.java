package org.radargun.stages.topology;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.traits.Clustered;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.TopologyHistory;
import org.radargun.utils.TimeConverter;

@Stage(doc = "Waits for a period without any change in membership/topology history.")
public class WaitForTopologySettleStage extends AbstractDistStage {

   @Property(doc = "How long period without any change are we looking for. Default is 10 seconds.", converter = TimeConverter.class)
   private long period = 10000;

   @Property(doc = "How long should we wait until we give up with error, 0 means indefinitely. Default is 10 minutes.", converter = TimeConverter.class)
   private long timeout = 600000;

   @Property(doc = "Name of the cache where we detect the events. Default is the default cache.")
   private String cacheName;

   @Property(doc = "Wait for cache topology settle. Default is true.")
   private boolean checkTopology = true;

   @Property(doc = "Wait for data rehashes to settle. Default is true.")
   private boolean checkDataRehash = true;

   @Property(doc = "Wait for cluster membership to settle. Default is true (if the Clustered trait is supported).")
   private boolean checkMembership = true;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private TopologyHistory history;

   @InjectTrait
   private Clustered clustered;

   @Override
   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         return successfulResponse();
      }
      long startTime = System.currentTimeMillis();
      for (;;) {
         long now = System.currentTimeMillis();
         if (now >= startTime + timeout) {
            return errorResponse("The topology has not settled within timeout.");
         }
         boolean settled = true;

         List<TopologyHistory.Event> topologyChangeHistory = history.getTopologyChangeHistory(cacheName);
         List<TopologyHistory.Event> rehashHistory = history.getRehashHistory(cacheName);
         List<Clustered.Membership> membershipHistory = clustered == null ? null : clustered.getMembershipHistory();
         if (checkTopology && topologyChangeHistory != null && !topologyChangeHistory.isEmpty()) {
            TopologyHistory.Event event = topologyChangeHistory.get(topologyChangeHistory.size() - 1);
            if (event.getEnded() == null) {
               log.debug("Topology change event has not finished: " + event);
               settled = false;
            } else if (now < event.getEnded().getTime() + period) {
               log.debug("Last topology change event finished too recently: " + event);
               settled = false;
            }
         }
         if (checkDataRehash && rehashHistory != null && !rehashHistory.isEmpty()) {
            TopologyHistory.Event event = rehashHistory.get(rehashHistory.size() - 1);
            if (event.getEnded() == null) {
               log.debug("Rehash event has not finished: " + event);
               settled = false;
            } else if (now < event.getEnded().getTime() + period) {
               log.debug("Last rehash event finished too recently: " + event);
               settled = false;
            }
         }
         if (checkMembership && membershipHistory != null && !membershipHistory.isEmpty()) {
            Clustered.Membership membership = membershipHistory.get(membershipHistory.size() - 1);
            if (now < membership.date.getTime() + period) {
               log.debug("Last membership changed too recently: " + membership);
               settled = false;
            }
         }

         if (settled) {
            return successfulResponse();
         } else {
            try {
               Thread.sleep(1000);
            } catch (InterruptedException e) {
               return errorResponse("Waiting interrupted", e);
            }
         }
      }
   }
}
