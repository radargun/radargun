package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Service(doc = JDG63EmbeddedService.SERVICE_DESCRIPTION)
public class JDG63EmbeddedService extends Infinispan60EmbeddedService {
   protected static final String SERVICE_DESCRIPTION = "Service hosting JDG in embedded (library) mode.";

   @Override
   @ProvidesTrait
   public Infinispan70MapReduce createMapReduce() {
      return new Infinispan70MapReduce(this);
   }

// TODO: Add this when the next ER is released
//   @Override
//   @ProvidesTrait
//   public InfinispanCacheInfo createCacheInformation() {
//      return new Infinispan70CacheInfo(this);
//   }

}
