package org.radargun.service;

import org.infinispan.AdvancedCache;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.radargun.traits.Query;

/**
 * Queryable implementation for embedded mode.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class InfinispanEmbeddedQueryable extends AbstractInfinispanQueryable {
   protected Infinispan52EmbeddedService service;

   public InfinispanEmbeddedQueryable(Infinispan52EmbeddedService service) {
      this.service = service;
   }

   @Override
   public Query.Builder getBuilder(String containerName, String queryString) {
      throw new UnsupportedOperationException("Supported only on Infinispan version equals or greater than 11");
   }

   @Override
   public Query.Context createContext(String containerName) {
      return new EmbeddedQueryContext(service.getCache(containerName).getAdvancedCache());
   }

   @Override
   public void reindex(String containerName) {
      SearchManager searchManager = Search.getSearchManager(service.getCache(containerName));
      searchManager.getMassIndexer().start();
   }

   public static class EmbeddedQueryContext implements Query.Context, AdvancedCacheHolder {
      private final AdvancedCache cache;

      public EmbeddedQueryContext(AdvancedCache cache) {
         this.cache = cache;
      }

      @Override
      public AdvancedCache getAdvancedCache() {
         return cache;
      }

      @Override
      public void close() {
      }
   }
}
