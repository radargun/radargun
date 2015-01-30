package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan70EmbeddedService extends Infinispan60EmbeddedService {
   @SuppressWarnings("rawtypes")
   @Override
   @ProvidesTrait
   public Infinispan70MapReduce createMapReduce() {
      return new Infinispan70MapReduce(this);
   }

   @Override
   @ProvidesTrait
   public InfinispanCacheInfo createCacheInformation() {
      return new Infinispan70CacheInfo(this);
   }

   @Override
   @ProvidesTrait
   public InfinispanEmbeddedQueryable createQueryable() {
      return new Infinispan70EmbeddedQueryable(this);
   }

   @ProvidesTrait
   public InfinispanIterable createIterable() {
      return new InfinispanIterable(this);
   }

   @ProvidesTrait
   public InfinispanCacheListeners createListeners() {
      return new InfinispanCacheListeners(this);
   }

}
