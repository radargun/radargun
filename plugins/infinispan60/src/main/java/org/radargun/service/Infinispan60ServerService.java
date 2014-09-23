package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

@Service(doc = Infinispan60ServerService.SERVICE_DESCRIPTION)
public class Infinispan60ServerService extends InfinispanServerService {
   protected Infinispan60ServerTopologyHistory topologyHistory = new Infinispan60ServerTopologyHistory(this);

   @ProvidesTrait
   public Infinispan60ServerTopologyHistory getTopologyHistory() {
      return topologyHistory;
   }


}
