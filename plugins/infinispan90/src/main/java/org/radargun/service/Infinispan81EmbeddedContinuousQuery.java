package org.radargun.service;

import org.infinispan.Cache;
import org.radargun.traits.ContinuousQuery;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
public abstract class Infinispan81EmbeddedContinuousQuery implements ContinuousQuery {

   protected final InfinispanEmbeddedService service;

   public Infinispan81EmbeddedContinuousQuery(InfinispanEmbeddedService service) {
      this.service = service;
   }

   protected Cache<Object, Object> getCache(String cacheName) {
      return service.getCache(cacheName);
   }
}
