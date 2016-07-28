package org.radargun.service;

import org.infinispan.AdvancedCache;

public class Infinispan51Transactional extends InfinispanTransactional {
   protected final Infinispan51EmbeddedService service;

   public Infinispan51Transactional(Infinispan51EmbeddedService service) {
      super(service);
      this.service = service;
   }

   @Override
   public Configuration getConfiguration(String cacheName) {
      if (service.batching && service.isCacheBatching(service.getCache(cacheName))) {
         return Configuration.TRANSACTIONAL;
      }
      return super.getConfiguration(cacheName);
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
         if (trace) log.trace("Starting batch");
         impl.startBatch();
      }

      @Override
      public void commit() {
         if (trace) log.trace("Committing batch");
         impl.endBatch(true);
      }

      @Override
      public void rollback() {
         if (trace) log.trace("Rolling back batch");
         impl.endBatch(false);
      }
   }
}
