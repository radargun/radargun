package org.radargun.service;

import org.infinispan.query.Search;

/**
 * Supports non-indexed queries.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan70EmbeddedQueryable extends InfinispanEmbeddedQueryable {
   public Infinispan70EmbeddedQueryable(Infinispan52EmbeddedService service) {
      super(service);
   }

   @Override
   public QueryBuilder getBuilder(String cacheName, Class<?> clazz) {
      return new QueryBuilderImpl(Search.getQueryFactory(service.getCache(cacheName)), clazz);
   }
}
