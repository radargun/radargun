package org.radargun.service;

import org.infinispan.Cache;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.Query;

/**
  * We cannot extend Infinispan81EmbeddedContinuousQuery because of changes in infinispan query api.
  * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
  */
public class Infinispan90EmbeddedContinuousQuery implements ContinuousQuery {

   protected final InfinispanEmbeddedService service;
   private org.infinispan.query.api.continuous.ContinuousQuery<Object, Object> cq;
   private IspnContinuousQueryListener ispnCqListener;

   public Infinispan90EmbeddedContinuousQuery(InfinispanEmbeddedService service) {
      this.service = service;
   }

   public void createContinuousQuery(String cacheName, Query query, ContinuousQuery.ContinuousQueryListener cqListener) {
      AbstractInfinispanQueryable.QueryImpl ispnQuery = (AbstractInfinispanQueryable.QueryImpl) query;
      cq = new org.infinispan.query.continuous.impl.ContinuousQueryImpl<Object, Object>(getCache(cacheName));
      ispnCqListener = new IspnContinuousQueryListener(cqListener);
      cq.addContinuousQueryListener(ispnQuery.getDelegatingQuery(), ispnCqListener);
   }

   public void removeContinuousQuery(String cacheName, Object cqListener) {
      if (cq != null && ispnCqListener != null) {
         cq.removeContinuousQueryListener(ispnCqListener);
      }
   }

   protected Cache<Object, Object> getCache(String cacheName) {
      return service.getCache(cacheName);
   }

   public static class IspnContinuousQueryListener implements org.infinispan.query.api.continuous.ContinuousQueryListener {

      private final ContinuousQuery.ContinuousQueryListener cqListener;

      public IspnContinuousQueryListener(ContinuousQuery.ContinuousQueryListener cqListener) {
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
}