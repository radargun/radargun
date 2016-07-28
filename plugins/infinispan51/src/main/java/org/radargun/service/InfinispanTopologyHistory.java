package org.radargun.service;

import org.infinispan.Cache;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.DataRehashed;
import org.infinispan.notifications.cachelistener.annotation.TopologyChanged;
import org.infinispan.notifications.cachelistener.event.DataRehashedEvent;
import org.infinispan.notifications.cachelistener.event.TopologyChangedEvent;
import org.radargun.traits.TopologyHistory;

public class InfinispanTopologyHistory extends AbstractTopologyHistory {
   protected final Infinispan51EmbeddedService service;

   public InfinispanTopologyHistory(Infinispan51EmbeddedService service) {
      this.service = service;
   }

   public void registerListener(Cache<?, ?> cache) {
      cache.addListener(new TopologyAwareListener(cache.getName()));
   }

   @Override
   protected String getDefaultCacheName() {
      return service.getCache(null).getName();
   }

   @Listener
   public class TopologyAwareListener {
      protected final String cacheName;

      public TopologyAwareListener(String cacheName) {
         this.cacheName = cacheName;
      }

      @TopologyChanged
      public void onTopologyChanged(TopologyChangedEvent<?, ?> e) {
         log.debug("Topology change " + (e.isPre() ? "started" : "finished"));
         int atStart = service.membersCount(e.getConsistentHashAtStart());
         int atEnd = service.membersCount(e.getConsistentHashAtEnd());
         TopologyHistory.Event.EventType type = e.isPre() ? TopologyHistory.Event.EventType.START : TopologyHistory.Event.EventType.END;
         addEvent(topologyChanges, cacheName, type, atStart, atEnd);
      }

      @DataRehashed
      public void onDataRehashed(DataRehashedEvent<?, ?> e) {
         log.debug("Rehash " + (e.isPre() ? "started" : "finished"));
         int atStart = e.getMembersAtStart().size();
         int atEnd = e.getMembersAtEnd().size();
         TopologyHistory.Event.EventType type = e.isPre() ? TopologyHistory.Event.EventType.START : TopologyHistory.Event.EventType.END;
         addEvent(hashChanges, cacheName, type, atStart, atEnd);
      }
   }
}
