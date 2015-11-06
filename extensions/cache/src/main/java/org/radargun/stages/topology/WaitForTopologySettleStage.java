package org.radargun.stages.topology;

import java.util.EnumSet;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.traits.Clustered;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.TopologyHistory;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;

import static org.radargun.traits.TopologyHistory.HistoryType;

@Stage(doc = "Waits for a period without any change in membership/topology history.")
public class WaitForTopologySettleStage extends AbstractDistStage {

   @Property(doc = "How long period without any change are we looking for. Default is 10 seconds.", converter = TimeConverter.class)
   public long period = 10000;

   @Property(doc = "How long should we wait until we give up with error, 0 means indefinitely. Default is 10 minutes.", converter = TimeConverter.class)
   public long timeout = 600000;

   @Property(doc = "Name of the cache where we detect the events. Default is the default cache.")
   public String cacheName;

   @Property(doc = "Type of events to check in this stage. Default are TOPOLOGY, REHASH, CACHE_STATUS (see org.radargun.traits.TopologyHistory.HistoryType).")
   public EnumSet<TopologyHistory.HistoryType> checkEvents = EnumSet.allOf(TopologyHistory.HistoryType.class);

   @Property(doc = "Wait for cluster membership to settle. Default is true (if the Clustered trait is supported).")
   public boolean checkMembership = true;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private TopologyHistory history;

   @InjectTrait
   private Clustered clustered;

   @Override
   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         return successfulResponse();
      }
      long startTime = TimeService.currentTimeMillis();
      for (;;) {
         long now = TimeService.currentTimeMillis();
         if (now >= startTime + timeout) {
            return errorResponse("The topology has not settled within timeout.");
         }
         boolean settled = true;

         if (checkEvents.contains(HistoryType.TOPOLOGY)) {
            settled = settled && checkEventHistory(history.getTopologyChangeHistory(cacheName), "Topology change", now);
         }
         if (checkEvents.contains(HistoryType.REHASH)) {
            settled = settled && checkEventHistory(history.getRehashHistory(cacheName), "Rehash", now);
         }
         if (checkEvents.contains(HistoryType.CACHE_STATUS)) {
            settled = settled && checkEventHistory(history.getCacheStatusChangeHistory(cacheName), "Cache status change", now);
         }
         if (checkMembership) {
            List<Clustered.Membership> membershipHistory = clustered == null ? null : clustered.getMembershipHistory();
            settled = settled && checkMembership(membershipHistory, now);
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

   /**
    * @return Boolean indicating whether Topology/Rehash/Partition status has settled.
    */
   private boolean checkEventHistory(List<TopologyHistory.Event> eventList, String eventName, long startDateMillis) {
      int finished = 0;
      for (int i = eventList.size() - 1; i >= 0; i--) {
         TopologyHistory.Event event = eventList.get(i);
         if (startDateMillis < event.getTime().getTime() + period) {
            log.debugf("%s event finished too recently: %s", eventName, event);
            return false;
         }
         switch (event.getType()) {
            case START: {
               if (finished <= 0) {
                  log.debugf("%s event has not finished: %s", eventName, event);
                  return false;
               } else {
                  finished--;
               }
               break;
            }
            case END: {
               finished++;
               break;
            }
            case SINGLE: {
               // no op
            }
         }
      }
      if (finished != 0) {
         log.warn("Number of START / END events doesn't match");
      }
      return true;
   }

   private boolean checkMembership(List<Clustered.Membership> membershipHistory, long startDateMillis) {
      if (checkMembership && membershipHistory != null && !membershipHistory.isEmpty()) {
         Clustered.Membership membership = membershipHistory.get(membershipHistory.size() - 1);
         if (startDateMillis < membership.date.getTime() + period) {
            log.debug("Last membership changed too recently: " + membership);
            return false;
         }
      }
      return true;
   }
}
