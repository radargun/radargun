package org.radargun.service;

import org.infinispan.factories.ComponentRegistry;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanClustered implements Clustered {

   protected static final Log log = LogFactory.getLog(InfinispanClustered.class);

   protected final InfinispanEmbeddedService service;

   public InfinispanClustered(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public boolean isCoordinator() {
      return service.cacheManager.isCoordinator();
   }

   @Override
   public int getClusteredNodes() {
      ComponentRegistry componentRegistry = service.getCache(null).getAdvancedCache().getComponentRegistry();
      if (componentRegistry.getStatus().startingUp()) {
         log.trace("We're in the process of starting up.");
      }
      if (service.cacheManager.getMembers() != null) {
         log.trace("Members are: " + service.cacheManager.getMembers());
      }
      return service.cacheManager.getMembers() == null ? 0 : service.cacheManager.getMembers().size();
   }
}
