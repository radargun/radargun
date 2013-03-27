package org.radargun.stages;

import java.util.List;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.features.TopologyAware;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 2/25/13
 */
@Stage(doc = "Waits until some event occurs. Note that the initial rehash is not recorded in this manner" +
      ", therefore waiting for that will result in timeout.")
public class WaitForTopologyEventStage extends AbstractDistStage {

   private static final String LAST_REHASH = "__last_rehash__";
   private static final String LAST_TOPOOLOGY_UPDATE = "__last_tu__";
   public static final String SIZE_KEY = "size";
   public static final String EVENT_KEY = "event";

   public enum Type {
      REHASH,
      TOPOLOGY_UPDATE
   }

   public enum Condition {
      START,
      END
   }

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

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      CacheWrapper wrapper = slaveState.getCacheWrapper();
      if (wrapper == null) {
         return ack;
      }
      if (!(wrapper instanceof TopologyAware)) {
         String message = "This wrapper is not topology aware! Cannot use this stage.";
         log.error(message);
         ack.setErrorMessage(message);
         ack.setError(true);
         return ack;
      }
      TopologyAware taWrapper = (TopologyAware) wrapper;

      List<TopologyAware.Event> history = getEventHistory(taWrapper);
      // we grab the size in order to have synchronized event and size;
      int historySize = history.size();
      TopologyAware.Event lastEvent = historySize == 0 ? null : history.get(historySize - 1).copy();
      if (wait) {
         int lastSeenSize = intOrZero(slaveState.get(getTypeKey() + SIZE_KEY));
         TopologyAware.Event lastSeenEvent = (TopologyAware.Event) slaveState.get(getTypeKey() + EVENT_KEY);
         long startWaiting = System.currentTimeMillis();

         wait_loop:
         while (timeout <= 0 || System.currentTimeMillis() < startWaiting + timeout) {
            if (historySize > 0) {
               if (condition == Condition.END) {
                  for (int i = historySize - 1; i >= lastSeenSize; --i) {
                     TopologyAware.Event e = history.get(i);
                     if (e.getEnded() != null && e.getMembersAtEnd() >= minMembers && e.getMembersAtEnd() <= maxMembers) {
                        break wait_loop;
                     }
                  }
                  if (lastSeenSize > 0) {
                     TopologyAware.Event lastSeenEventNow = history.get(lastSeenSize - 1);
                     if (lastSeenEvent.getEnded() == null && lastSeenEventNow.getEnded() != null
                           && lastSeenEventNow.getMembersAtEnd() >= minMembers && lastSeenEventNow.getMembersAtEnd() <= maxMembers) {
                        break wait_loop;
                     }
                  }
               } else if (condition == Condition.START) {
                  for (int i = historySize - 1; i >= lastSeenSize; --i) {
                     TopologyAware.Event e = history.get(i);
                     if (e.getMembersAtEnd() >= minMembers && e.getMembersAtEnd() <= maxMembers) {
                        break wait_loop;
                     }
                  }
               }
            }
            log.trace("Waiting... lastDetected=" + lastSeenEvent + " (" + lastSeenSize
                            + "), lastHistory=" + lastEvent + " (" + historySize + ")");
            try {
               Thread.sleep(1000);
            } catch (InterruptedException e) {
               String message = "Waiting was interrupted";
               log.error(message, e);
               ack.setErrorMessage(message);
               ack.setError(true);
               ack.setRemoteException(e);
               return ack;
            }
            historySize = history.size();
            lastEvent = historySize == 0 ? null : history.get(historySize - 1).copy();
         }
         /* end of wait_loop */
         if (timeout > 0 && System.currentTimeMillis() > startWaiting + timeout) {
            String message = "Waiting has timed out";
            log.error(message);
            ack.setError(true);
            ack.setErrorMessage(message);
            return ack;
         }
      }
      if (set) {
         slaveState.put(getTypeKey() + SIZE_KEY, historySize);
         slaveState.put(getTypeKey() + EVENT_KEY, lastEvent);
      }
      return ack;
   }

   private int intOrZero(Object o) {
      return o == null ? 0 : (Integer) o;
   }

   private String getTypeKey() {
      switch (type) {
         case REHASH:
            return LAST_REHASH;
         case TOPOLOGY_UPDATE:
            return LAST_TOPOOLOGY_UPDATE;
      }
      throw new IllegalStateException();
   }

   private List<TopologyAware.Event> getEventHistory(TopologyAware wrapper) {
      switch (type) {
         case REHASH:
            return wrapper.getRehashHistory();
         case TOPOLOGY_UPDATE:
            return wrapper.getTopologyChangeHistory();
      }
      throw new IllegalStateException();
   }
}
