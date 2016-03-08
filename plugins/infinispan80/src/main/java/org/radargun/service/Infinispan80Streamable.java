package org.radargun.service;

import java.util.stream.Stream;

import org.radargun.traits.Streamable;

/**
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
public class Infinispan80Streamable implements Streamable {

   private final InfinispanEmbeddedService service;

   public Infinispan80Streamable(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public Stream stream(String resourceName) {
      return service.getCache(resourceName).entrySet().stream();
   }

   @Override
   public Stream parallelStream(String resourceName) {
      return service.getCache(resourceName).entrySet().parallelStream();
   }
}
