package org.radargun.service;

import org.radargun.Service;

/**
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class JDG63EmbeddedService extends Infinispan60EmbeddedService {

   @Override
   public Infinispan70MapReduce createMapReduce() {
      return new Infinispan70MapReduce(this);
   }

}
