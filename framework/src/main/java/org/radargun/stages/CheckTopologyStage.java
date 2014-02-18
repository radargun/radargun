package org.radargun.stages;

import java.util.Date;
import java.util.List;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.features.TopologyAware;
import org.radargun.features.TopologyAware.Event;

/**
 * Controls which topology events have (not) happened recently
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Controls which topology events have (not) happened recently")
public class CheckTopologyStage extends AbstractDistStage {

   enum Type {
      HASH_AND_TOPOLOGY,
      HASH,
      TOPOLOGY
   }

   @Property(doc = "What does this stage control. Default is both DataRehashed and TopologyChanged events.")
   private Type type = Type.HASH_AND_TOPOLOGY;

   @Property(converter = TimeConverter.class, doc = "The period in milliseconds which is checked. Default is infinite.")
   private long period = Long.MAX_VALUE;

   @Property(doc = "The check controls if this event has happened (true) or not happened (false). Defaults to true.")
   private boolean changed = true;
   
   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (!slaves.contains(slaveState.getSlaveIndex())) {
         log.debug("Ignoring this slave");
         return ack;
      }
      CacheWrapper wrapper = slaveState.getCacheWrapper();
      if (!(wrapper instanceof TopologyAware)) {
         String message = "CacheWrapper is not aware of topology/hash changes";
         log.error(message);
         ack.setError(true);
         ack.setErrorMessage(message);
         return ack;
      }
      TopologyAware ta = (TopologyAware) wrapper;
      if (type == Type.HASH_AND_TOPOLOGY || type == Type.TOPOLOGY) {
         List<Event> history = ta.getTopologyChangeHistory(); 
         if (!check(history)) {
            String message = "Topology check failed, " + (history.isEmpty() ? "no change in history" : "last change " + history.get(history.size() - 1));
            log.error(message);
            ack.setError(true);
            ack.setErrorMessage(message);
            return ack;
         } else {
            log.debug("Topology check passed.");
         }
      }
      if (type == Type.HASH_AND_TOPOLOGY || type == Type.HASH) {
         List<Event> history = ta.getRehashHistory(); 
         if (!check(history)) {
            String message = "Hash check failed, " + (history.isEmpty() ? "no change in history" : "last change " + history.get(history.size() - 1));
            log.error(message);
            ack.setError(true);
            ack.setErrorMessage(message);
            return ack;
         } else {
            log.debug("Hash check passed.");
         }
      }
      return ack;
   }

   private boolean check(List<Event> history) {
      if (history.isEmpty()) return !changed;
      long lastChange = history.get(history.size() - 1).getEnded().getTime();
      long current = new Date().getTime();
      boolean hasChanged = lastChange + period > current; 
      return hasChanged == changed;
   }
}
