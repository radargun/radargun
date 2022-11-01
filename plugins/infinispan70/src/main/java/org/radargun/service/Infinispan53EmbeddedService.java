package org.radargun.service;

import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class Infinispan53EmbeddedService extends Infinispan52EmbeddedService {
   @ProvidesTrait
   @Override
   public Infinispan53MapReduce createMapReduce() {
      return new Infinispan53MapReduce(this);
   }

   @Override
   public abstract InfinispanCacheInfo createCacheInformation();
}
