package org.radargun.stages;

import java.util.Date;
import java.util.List;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.features.TopologyAware;
import org.radargun.features.TopologyAware.Event;

public class CheckTopologyStage extends AbstractDistStage {

   enum Type {
      HASH_AND_TOPOLOGY,
      HASH,
      TOPOLOGY
   }
   
   private Type type = Type.HASH_AND_TOPOLOGY;
   private long period = Long.MAX_VALUE;
   private boolean changed = true;
   
   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (!slaves.contains(getSlaveIndex())) {
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

   public void setType(String type) {
      this.type = Enum.valueOf(Type.class, type);
   }

   public void setPeriod(long period) {
      this.period = period;
   }

   public void setChanged(boolean changed) {
      this.changed = changed;
   }

}
