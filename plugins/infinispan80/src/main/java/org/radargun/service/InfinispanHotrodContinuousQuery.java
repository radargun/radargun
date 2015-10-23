package org.radargun.service;


import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.event.ClientEvents;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.Query;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
public class InfinispanHotrodContinuousQuery implements ContinuousQuery {

    protected final InfinispanHotrodService service;
    private Object clientListener;

    public InfinispanHotrodContinuousQuery(InfinispanHotrodService service) {
        this.service = service;
    }

    @Override
    public void createContinuousQuery(String cacheName, Query query, ContinuousQueryListener cqListener) {
        AbstractInfinispanQueryable.QueryImpl ispnQuery = (AbstractInfinispanQueryable.QueryImpl) query;
        HotRodContinuousQueryListener ispnCqListener = new HotRodContinuousQueryListener(cqListener);
        clientListener = ClientEvents.addContinuousQueryListener(getRemoteCache(cacheName), ispnCqListener, ispnQuery.getDelegatingQuery());
    }

    @Override
    public void removeContinuousQuery(String cacheName, Object cqListener) {
        if (clientListener != null) {
            getRemoteCache(cacheName).removeClientListener(cqListener);
        }
    }

    protected RemoteCache getRemoteCache(String cacheName) {
        return cacheName == null ? service.managerNoReturn.getCache() : service.managerNoReturn.getCache(cacheName);
    }

    public static class HotRodContinuousQueryListener implements org.infinispan.client.hotrod.event.ContinuousQueryListener {

        private final ContinuousQueryListener cqListener;

        public HotRodContinuousQueryListener(ContinuousQueryListener cqListener) {
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
