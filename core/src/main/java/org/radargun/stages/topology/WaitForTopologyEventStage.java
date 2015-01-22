package org.radargun.stages.topology;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.utils.TimeConverter;
import org.radargun.stages.AbstractDistStage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.TopologyHistory;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Waits until some event occurs. Note that the initial rehash is not recorded in this manner, " +
      "therefore waiting for that will result in timeout.")
public class WaitForTopologyEventStage extends AbstractDistStage {
   public enum Type {
      REHASH("__last_rehash_event__"),
      TOPOLOGY_UPDATE("__last_topology_event__");

      private final String key;

      private Type(String key) {
         this.key = key;
      }

      public String getKey() {
         return key;
      }
   }

   public enum Condition {
      START,
      END
   }

   @Property(doc = "Name of the cache where we detect the events. Default is the default cache.")
   private String cacheName;

   @Property(doc = "Wait for the event to happen. Default is true.")
   private boolean wait = true;

   @Property(doc = "Set last state before finishing. Default is true.")
   private boolean set = true;

   @Property(doc = "Type of event we are detecting. Default is REHASH.")
   private Type type = Type.REHASH;

   @Property(doc = "Condition we are waiting for. Default is END.")
   private Condition condition = Condition.END;

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
         TopologyHistory.Event setEvent = (TopologyHistory.Event) slaveState.get(type.getKey());
         long startWaiting = System.currentTimeMillis();

         wait_loop:
         while (timeout <= 0 || System.currentTimeMillis() < startWaiting + timeout) {
            log.trace("setEvent=" + setEvent + ", history=" + history);
            if (history.size() > 0) {
               if (condition == Condition.END) {
                  for (int i = history.size() - 1; i >= 0; --i) {
                     TopologyHistory.Event e = history.get(i);
                     if (setEvent != null && setEvent.getEnded() != null && !e.getStarted().after(setEvent.getStarted())) break;
                     if (e.getEnded() != null && e.getMembersAtEnd() >= minMembers && e.getMembersAtEnd() <= maxMembers) {
                        break wait_loop;
                     }
                  }
               } else if (condition == Condition.START) {
                  for (int i = history.size() - 1; i >= 0; --i) {
                     TopologyHistory.Event e = history.get(i);
                     if (setEvent != null && !e.getStarted().after(setEvent.getStarted())) break;
                     if (e.getMembersAtEnd() >= minMembers && e.getMembersAtEnd() <= maxMembers) {
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
         if (timeout > 0 && System.currentTimeMillis() > startWaiting + timeout) {
            return errorResponse("Waiting has timed out");
         }
      }
      if (set) {
         slaveState.put(type.getKey(), history.isEmpty() ? null : history.get(history.size() - 1));
      }
      return successfulResponse();
   }

   private List<TopologyHistory.Event> getEventHistory(TopologyHistory wrapper) {
      switch (type) {
         case REHASH:
            return wrapper.getRehashHistory(cacheName);
         case TOPOLOGY_UPDATE:
            return wrapper.getTopologyChangeHistory(cacheName);
      }
      throw new IllegalStateException();
   }
}
