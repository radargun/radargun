package org.radargun.service;

import org.infinispan.Cache;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.Query;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
public class Infinispan81EmbeddedContinuousQuery extends Infinispan80EmbeddedContinuousQuery {

    protected final InfinispanEmbeddedService service;
    private org.infinispan.query.continuous.ContinuousQuery<Object, Object> cq;
    private IspnContinuousQueryListener ispnCqListener;

    public Infinispan81EmbeddedContinuousQuery(InfinispanEmbeddedService service) {
        super(service);
        this.service = service;
    }

    @Override
    public void createContinuousQuery(String cacheName, Query query, ContinuousQueryListener cqListener) {
        AbstractInfinispanQueryable.QueryImpl ispnQuery = (AbstractInfinispanQueryable.QueryImpl) query;
        cq = new org.infinispan.query.continuous.ContinuousQuery<Object, Object>(getCache(cacheName));
        ispnCqListener = new IspnContinuousQueryListener(cqListener);
        cq.addContinuousQueryListener(ispnQuery.getDelegatingQuery(), ispnCqListener);
    }

    public static class IspnContinuousQueryListener implements org.infinispan.query.continuous.ContinuousQueryListener {

        private final ContinuousQueryListener cqListener;

        public IspnContinuousQueryListener(ContinuousQueryListener cqListener) {
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
