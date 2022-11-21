package org.radargun.service;

import org.infinispan.Cache;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.PartitionStatusChanged;
import org.infinispan.notifications.cachelistener.event.PartitionStatusChangedEvent;
import org.radargun.traits.TopologyHistory;

/**
 * @author Matej Cimbora
 */
public class Infinispan70TopologyHistory extends InfinispanTopologyHistory {

   public Infinispan70TopologyHistory(Infinispan60EmbeddedService service) {
      super(service);
   }

   @Override
   public void registerListener(Cache<?, ?> cache) {
      cache.addListener(new Infinispan70TopologyAwareListener(cache.getName()));
   }

   @Listener
   public class Infinispan70TopologyAwareListener extends TopologyAwareListener {

      public Infinispan70TopologyAwareListener(String cacheName) {
         super(cacheName);
      }

      @PartitionStatusChanged
      public void onPartitionStatusChanged(PartitionStatusChangedEvent<?, ?> e) {
         log.debug("Partition status update " + (e.isPre() ? "started" : "finished"));
         TopologyHistory.Event.EventType type = e.isPre() ? TopologyHistory.Event.EventType.START : TopologyHistory.Event.EventType.END;
         addEvent(cacheStatusChanges, cacheName, type, 0, 0);
      }
   }
}
