package org.radargun.service;

import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.infinispan.query.dsl.QueryFactory;

/**
 * Queryable implementation for embedded mode.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanEmbeddedQueryable extends AbstractInfinispanQueryable {
   protected Infinispan52EmbeddedService service;

   public InfinispanEmbeddedQueryable(Infinispan52EmbeddedService service) {
      this.service = service;
   }

   @Override
   public QueryBuilder getBuilder(String containerName, Class<?> clazz) {
      SearchManager searchManager = Search.getSearchManager(service.getCache(containerName));
      QueryFactory factory = searchManager.getQueryFactory();
      return new QueryBuilderImpl(factory, clazz);
   }

   @Override
   public void reindex(String containerName) {
      SearchManager searchManager = Search.getSearchManager(service.getCache(containerName));
      searchManager.getMassIndexer().start();
   }
}
