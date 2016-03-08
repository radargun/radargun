package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * @author vjuranek
 */
@Service(doc = JDG64EmbeddedService.SERVICE_DESCRIPTION)
public class JDG64EmbeddedService extends JDG63EmbeddedService {
   protected static final String SERVICE_DESCRIPTION = "Service hosting JDG in embedded (library) mode.";

   @ProvidesTrait
   public InfinispanCacheListeners createListeners() {
      return new InfinispanCacheListeners(this);
   }

   @ProvidesTrait
   public Infinispan70TopologyHistory getInfinispan70TopologyHistory() {
      return (Infinispan70TopologyHistory) topologyAware;
   }

   @Override
   protected InfinispanTopologyHistory createTopologyAware() {
      return new Infinispan70TopologyHistory(this);
   }

}
