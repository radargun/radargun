package org.radargun.service;

import org.infinispan.Cache;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.Query;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
public class Infinispan81EmbeddedContinuousQuery implements ContinuousQuery {

   protected final InfinispanEmbeddedService service;

   public Infinispan81EmbeddedContinuousQuery(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public ListenerReference createContinuousQuery(String cacheName, Query query, ContinuousQuery.Listener cqListener) {
      AbstractInfinispanQueryable.QueryImpl ispnQuery = (AbstractInfinispanQueryable.QueryImpl) query;
      org.infinispan.query.continuous.ContinuousQuery cq = new org.infinispan.query.continuous.ContinuousQuery(getCache(cacheName));
      Listener ispnCqListener = new Listener(cqListener);
      cq.addContinuousQueryListener(ispnQuery.getDelegatingQuery(), ispnCqListener);
      return new ListenerReference(cq, ispnCqListener);
   }

   @Override
   public void removeContinuousQuery(String cacheName, ContinuousQuery.ListenerReference listenerReference) {
      ListenerReference ref = (ListenerReference) listenerReference;
      ref.cq.removeContinuousQueryListener(ref.listener);
   }

   protected Cache<Object, Object> getCache(String cacheName) {
      return service.getCache(cacheName);
   }

   private static class Listener implements org.infinispan.query.continuous.ContinuousQueryListener {

      private final ContinuousQuery.Listener cqListener;

      public Listener(ContinuousQuery.Listener cqListener) {
         this.cqListener = cqListener;
      }

      @Override
      public void resultJoining(Object key, Object value) {
         cqListener.onEntryJoined(key, value);
      }

      @Override
      public void resultLeaving(Object key) {
         cqListener.onEntryLeft(key);
      }
   }

   private static class ListenerReference implements ContinuousQuery.ListenerReference {
      private final org.infinispan.query.continuous.ContinuousQuery<Object, Object> cq;
      private final Listener listener;

      private ListenerReference(org.infinispan.query.continuous.ContinuousQuery<Object, Object> cq, Listener listener) {
         this.cq = cq;
         this.listener = listener;
      }
   }
}
