package org.radargun.stages.topology;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.TopologyHistory;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;

import static org.radargun.traits.TopologyHistory.Event.EventType;
import static org.radargun.traits.TopologyHistory.HistoryType;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Waits until some event occurs. Note that the initial rehash is not recorded in this manner, " +
   "therefore waiting for that will result in timeout.")
public class WaitForTopologyEventStage extends AbstractDistStage {

   @Property(doc = "Name of the cache where we detect the events. Default is the default cache.")
   private String cacheName;

   @Property(doc = "Wait for the event to happen. Default is true.")
   private boolean wait = true;

   @Property(doc = "Set last state before finishing. Default is true.")
   private boolean set = true;

   @Property(doc = "Type of event we are detecting. Default is REHASH (see org.radargun.traits.TopologyHistory.HistoryType).")
   private TopologyHistory.HistoryType type = HistoryType.REHASH;

   @Property(doc = "Condition we are waiting for. Default is END (see org.radargun.traits.TopologyHistory.Event.EventType).")
   private EventType condition = EventType.END;

   @Property(doc = "How long should we wait until we give up with error, 0 means indefinitely. Default is 10 minutes.", converter = TimeConverter.class)
   private long timeout = 600000;

   @Property(doc = "The minimum number of slaves that participated in this event. Default is 0.")
   private int minMembers = 0;

   @Property(doc = "The maximum number of slaves that participated in this event. Default is indefinite.")
   private int maxMembers = Integer.MAX_VALUE;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private TopologyHistory topologyHistory;

   @Override
   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         return successfulResponse();
      }
      List<TopologyHistory.Event> history = getEventHistory(topologyHistory);
      if (wait) {
         TopologyHistory.Event setEvent = (TopologyHistory.Event) slaveState.get(String.valueOf(type));
         long startWaiting = TimeService.currentTimeMillis();

         wait_loop:
         while (timeout <= 0 || TimeService.currentTimeMillis() < startWaiting + timeout) {
            log.trace("setEvent=" + setEvent + ", history=" + history);
            if (history.size() > 0) {
               if (condition == EventType.END) {
                  for (int i = history.size() - 1; i >= 0; --i) {
                     TopologyHistory.Event e = history.get(i);
                     if (setEvent != null && setEvent.getType() == EventType.END && !e.getTime().after(setEvent.getTime()))
                        break;
                     if (e.getType() == EventType.END && e.getMembersAtEnd() >= minMembers && e.getMembersAtEnd() <= maxMembers) {
                        break wait_loop;
                     }
                  }
               } else if (condition == EventType.START) {
                  for (int i = history.size() - 1; i >= 0; --i) {
                     TopologyHistory.Event e = history.get(i);
                     if (setEvent != null && setEvent.getType() == EventType.START && !e.getTime().after(setEvent.getTime()))
                        break;
                     if (e.getType() == EventType.START && e.getMembersAtEnd() >= minMembers && e.getMembersAtEnd() <= maxMembers) {
                        break wait_loop;
                     }
                  }
               } else if (condition == EventType.SINGLE) {
                  for (int i = history.size() - 1; i >= 0; --i) {
                     TopologyHistory.Event e = history.get(i);
                     if (setEvent != null && setEvent.getType() == EventType.SINGLE && !e.getTime().after(setEvent.getTime()))
                        break;
                     if (e.getType() == EventType.SINGLE && e.getMembersAtEnd() >= minMembers && e.getMembersAtEnd() <= maxMembers) {
                        break wait_loop;
                     }
                  }
               }
            }
            try {
               Thread.sleep(1000);
            } catch (InterruptedException e) {
               return errorResponse("Waiting was interrupted", e);
            }
            history = getEventHistory(topologyHistory);
         }
         /* end of wait_loop */
         if (timeout > 0 && TimeService.currentTimeMillis() > startWaiting + timeout) {
            return errorResponse("Waiting has timed out");
         }
      }
      if (set) {
         slaveState.put(String.valueOf(type), history.isEmpty() ? null : history.get(history.size() - 1));
      }
      return successfulResponse();
   }

   private List<TopologyHistory.Event> getEventHistory(TopologyHistory wrapper) {
      switch (type) {
         case TOPOLOGY:
            return wrapper.getTopologyChangeHistory(cacheName);
         case REHASH:
            return wrapper.getRehashHistory(cacheName);
         case CACHE_STATUS:
            return wrapper.getCacheStatusChangeHistory(cacheName);
      }
      throw new IllegalStateException("Unexpected event type " + type);
   }
}
