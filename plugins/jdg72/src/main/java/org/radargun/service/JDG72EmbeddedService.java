package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.MultimapCacheOperations;
import org.radargun.traits.ProvidesTrait;

@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class JDG72EmbeddedService  extends Infinispan92EmbeddedService {

   @ProvidesTrait
   public MultimapCacheOperations createMultimapBasicOperations() {
      return new JDG72MultimapCacheOperations(this);
   }
}
