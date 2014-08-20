package org.radargun.service;

import org.infinispan.AdvancedCache;

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
   public Transaction getTransaction() {
      if (service.batching) {
         return new Batch();
      } else {
         return super.getTransaction();
      }
   }

   protected class Batch implements Transaction {
      private AdvancedCache impl;

      @Override
      public <T> T wrap(T resource) {
         impl = getAdvancedCache(resource);
         return resource;
      }

      @Override
      public void begin() {
         impl.startBatch();
      }

      @Override
      public void commit() {
         impl.endBatch(true);
      }

      @Override
      public void rollback() {
         impl.endBatch(false);
      }
   }
}
