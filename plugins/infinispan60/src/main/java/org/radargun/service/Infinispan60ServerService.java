package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

@Service(doc = Infinispan60ServerService.SERVICE_DESCRIPTION)
public class Infinispan60ServerService extends InfinispanServerService {
   @ProvidesTrait
   public Infinispan60ServerTopologyHistory createTopologyHistory() {
      return new Infinispan60ServerTopologyHistory(this);
   }
}
