package org.radargun.stages.topology;

import java.util.EnumSet;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.utils.TimeConverter;
import org.radargun.stages.AbstractDistStage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.TopologyHistory;
import org.radargun.traits.TopologyHistory.Event;
import org.radargun.utils.TimeService;

import static org.radargun.traits.TopologyHistory.HistoryType;

/**
 * Controls which topology events have (not) happened recently
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Controls which topology events have (not) happened recently")
public class CheckTopologyStage extends AbstractDistStage {

   @Property(doc = "Name of the cache. Default is the default cache.")
   private String cacheName;

   @Property(doc = "Type of events to check in this stage. Default are TOPOLOGY, REHASH, CACHE_STATUS (see org.radargun.traits.TopologyHistory.HistoryType).")
   private EnumSet<TopologyHistory.HistoryType> checkEvents = EnumSet.allOf(TopologyHistory.HistoryType.class);

   @Property(converter = TimeConverter.class, doc = "The period in milliseconds which is checked. Default is infinite.")
   private long period = Long.MAX_VALUE;

   @Property(doc = "The check controls if this event has happened (true) or not happened (false). Defaults to true.")
   private boolean changed = true;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private TopologyHistory topologyHistory;

   @Override
   public DistStageAck executeOnSlave() {
      if (!shouldExecute()) {
         log.debug("Ignoring this slave");
         return successfulResponse();
      }
      if (checkEvents.contains(HistoryType.TOPOLOGY)) {
         List<Event> history = topologyHistory.getTopologyChangeHistory(cacheName);
         if (!check(history)) {
            return errorResponse("Topology check failed, " + (history.isEmpty() ? "no change in history" : "last change " + history.get(history.size() - 1)));
         } else {
            log.debug("Topology check passed.");
         }
      }
      if (checkEvents.contains(HistoryType.REHASH)) {
         List<Event> history = topologyHistory.getRehashHistory(cacheName);
         if (!check(history)) {
            return errorResponse("Hash check failed, " + (history.isEmpty() ? "no change in history" : "last change " + history.get(history.size() - 1)));
         } else {
            log.debug("Hash check passed.");
         }
      }
      if (checkEvents.contains(HistoryType.CACHE_STATUS)) {
         List<Event> history = topologyHistory.getCacheStatusChangeHistory(cacheName);
         if (!check(history)) {
            return errorResponse("Cache status check failed, " + (history.isEmpty() ? "no change in history" : "last change " + history.get(history.size() - 1)));
         } else {
            log.debug("Cache status check passed.");
         }
      }
      return successfulResponse();
   }

   private boolean check(List<Event> history) {
      if (history.isEmpty()) return !changed;
      long lastChange = history.get(history.size() - 1).getTime().getTime();
      long current = TimeService.currentTimeMillis();
      boolean hasChanged = lastChange + period > current;
      return hasChanged == changed;
   }
}
