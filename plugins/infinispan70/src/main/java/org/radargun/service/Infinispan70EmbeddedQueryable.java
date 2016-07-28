package org.radargun.service;

import org.infinispan.query.Search;
import org.radargun.traits.Query;

/**
 * Supports non-indexed queries.
 */
public class Infinispan70EmbeddedQueryable extends InfinispanEmbeddedQueryable {
   public Infinispan70EmbeddedQueryable(Infinispan52EmbeddedService service) {
      super(service);
   }

   @Override
   public Query.Builder getBuilder(String cacheName, Class<?> clazz) {
      return new QueryBuilderImpl(Search.getQueryFactory(service.getCache(cacheName)), clazz);
   }
}
