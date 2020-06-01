package org.radargun.service;

import org.infinispan.Cache;
import org.radargun.Service;

@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan110EmbeddedService extends Infinispan101EmbeddedService {

   // it was being used to init EvenSpreadingConsistentHash but EvenSpreadingConsistentHash was removed
   protected void injectEvenConsistentHash(Cache<Object, Object> cache) {
   }
}
