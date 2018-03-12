package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.MultimapCacheOperations;
import org.radargun.traits.ProvidesTrait;

@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan92EmbeddedService extends Infinispan91EmbeddedService {


   @ProvidesTrait
   public MultimapCacheOperations createMultimapBasicOperations() {
      return new Infinispan92MultimapCacheOperations(this);
   }
}