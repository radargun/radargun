package org.radargun.service;

import org.infinispan.AdvancedCache;
import org.radargun.traits.Transactional;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan51Transactional extends InfinispanTransactional {
   protected final Infinispan51EmbeddedService service;

   public Infinispan51Transactional(Infinispan51EmbeddedService service) {
      super(service);
      this.service = service;
   }

   @Override
   public Transactional.Resource getResource(String cacheName) {
      if (service.batching) {
         return new BatchingResource(service.getCache(cacheName).getAdvancedCache());
      } else {
         return super.getResource(cacheName);
      }
   }

   protected class BatchingResource implements Transactional.Resource {
      private final AdvancedCache impl;

      public BatchingResource(AdvancedCache cache) {
         this.impl = cache;
      }

      @Override
      public void startTransaction() {
         impl.startBatch();
      }

      @Override
      public void endTransaction(boolean successful) {
         impl.endBatch(successful);
      }
   }
}
