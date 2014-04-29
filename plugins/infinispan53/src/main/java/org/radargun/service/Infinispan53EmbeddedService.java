package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan53EmbeddedService extends Infinispan52EmbeddedService {
   @ProvidesTrait
   @Override
   public Infinispan53MapReduce createMapReduce() {
      return new Infinispan53MapReduce(this);
   }

   @ProvidesTrait
   @Override
   public InfinispanCacheInfo createCacheInformation() {
      return new Infinispan53CacheInfo(this);
   }
}
