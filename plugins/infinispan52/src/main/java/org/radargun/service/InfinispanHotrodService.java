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

   protected RemoteCacheManager manager;

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
      manager = new RemoteCacheManager(servers, true);
   }

   @Override
   public void stop() {
      manager.stop();
      manager = null;
   }

   @Override
   public boolean isRunning() {
      return manager != null && manager.isStarted();
   }
}
