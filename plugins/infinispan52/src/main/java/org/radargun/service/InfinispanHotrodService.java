package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = InfinispanHotrodService.SERVICE_DESCRIPTION)
public class InfinispanHotrodService implements Lifecycle {
   protected static final String SERVICE_DESCRIPTION = "HotRod client";

   @Property(name = "cache", doc = "Default cache name. By default, it's the default cache as retrived with getCache().")
   protected String cacheName;

   @Property(doc = "List of server addresses the clients should connect to, separated by semicolons (;).")
   protected String servers;

   // due to a bug in RCM, we have to duplicate the managers
   protected RemoteCacheManager managerNoReturn;
   protected RemoteCacheManager managerForceReturn;

   @ProvidesTrait
   public HotRodOperations createOperations() {
      return new HotRodOperations(this);
   }

   @ProvidesTrait
   public Lifecycle createLifecycle() {
      return this;
   }

   @Override
   public void start() {
      managerNoReturn = new RemoteCacheManager(servers, true);
      managerForceReturn = new RemoteCacheManager(servers, true);
   }

   @Override
   public void stop() {
      managerNoReturn.stop();
      managerNoReturn = null;
      managerForceReturn.stop();
      managerForceReturn = null;
   }

   @Override
   public boolean isRunning() {
      return managerNoReturn != null && managerNoReturn.isStarted();
   }
}
