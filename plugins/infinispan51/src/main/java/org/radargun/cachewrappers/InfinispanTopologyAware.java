package org.radargun.cachewrappers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.DataRehashed;
import org.infinispan.notifications.cachelistener.annotation.TopologyChanged;
import org.infinispan.notifications.cachelistener.event.DataRehashedEvent;
import org.infinispan.notifications.cachelistener.event.TopologyChangedEvent;
import org.radargun.features.TopologyAware;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanTopologyAware implements TopologyAware {

   protected final Log log = LogFactory.getLog(InfinispanBulkOperations.class);

   private List<TopologyAware.Event> topologyChanges = new ArrayList<Event>();
   private List<TopologyAware.Event> hashChanges = new ArrayList<TopologyAware.Event>();

   protected final Infinispan51Wrapper wrapper;

   public InfinispanTopologyAware(Infinispan51Wrapper wrapper) {
      this.wrapper = wrapper;
      wrapper.getCache(null).addListener(new TopologyAwareListener());
   }

   @Override
   public List<Event> getTopologyChangeHistory() {
      return Collections.unmodifiableList(topologyChanges);
   }

   @Override
   public List<TopologyAware.Event> getRehashHistory() {
      return Collections.unmodifiableList(hashChanges);
   }

   @Override
   public boolean isCoordinator() {
      return ((DefaultCacheManager) wrapper.getCacheManager()).isCoordinator();
   }

   @Listener
   public class TopologyAwareListener {
      @TopologyChanged
      public void onTopologyChanged(TopologyChangedEvent<?,?> e) {
         log.debug("Topology change " + (e.isPre() ? "started" : "finished"));
         int atStart = wrapper.membersCount(e.getConsistentHashAtStart());
         int atEnd = wrapper.membersCount(e.getConsistentHashAtEnd());
         addEvent(topologyChanges, e.isPre(), atStart, atEnd);
      }

      @DataRehashed
      public void onDataRehashed(DataRehashedEvent<?,?> e) {
         log.debug("Rehash " + (e.isPre() ? "started" : "finished"));
         int atStart = e.getMembersAtStart().size();
         int atEnd = e.getMembersAtEnd().size();
         addEvent(hashChanges, e.isPre(), atStart, atEnd);
      }

      private void addEvent(List<TopologyAware.Event> list, boolean isPre, int atStart, int atEnd) {
         if (isPre) {
            list.add(new Event(false, atStart, atEnd));
         } else {
            int size = list.size();
            if (size == 0 || list.get(size - 1).getEnded() != null) {
               Event ev = new Event(true, atStart, atEnd);
               list.add(ev);
            } else {
               ((Event) list.get(size - 1)).setEnded();
            }
         }
      }

      class Event extends TopologyAware.Event {
         private final Date started;
         private Date ended;
         private final int atStart;
         private final int atEnd;

         private Event(Date started, Date ended, int atStart, int atEnd) {
            this.started = started;
            this.ended = ended;
            this.atStart = atStart;
            this.atEnd = atEnd;
         }

         public Event(boolean finished, int atStart, int atEnd) {
            if (finished) {
               this.started = this.ended = new Date();
            } else {
               this.started = new Date();
            }
            this.atStart = atStart;
            this.atEnd = atEnd;
         }

         @Override
         public Date getStarted() {
            return started;
         }

         public void setEnded() {
            if (ended != null) throw new IllegalStateException();
            ended = new Date();
         }

         @Override
         public Date getEnded() {
            return ended;
         }

         @Override
         public int getMembersAtStart() {
            return atStart;
         }

         @Override
         public int getMembersAtEnd() {
            return atEnd;
         }

         @Override
         public TopologyAware.Event copy() {
            return new Event(started, ended, atStart, atEnd);
         }
      }
   }
}
