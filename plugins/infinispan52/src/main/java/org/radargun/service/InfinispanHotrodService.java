package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.radargun.Service;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = InfinispanHotrodService.SERVICE_DESCRIPTION)
public class InfinispanHotrodService {
   protected static final String SERVICE_DESCRIPTION = "HotRod client";

   @Property(doc = "List of server addresses the clients should connect to, separated by semicolons (;).")
   protected String servers;

   protected RemoteCacheManager manager;

   @Init
   public void init() {
      manager = new RemoteCacheManager(servers, true);
   }

   @ProvidesTrait
   public HotRodOperations createOperations() {
      return new HotRodOperations(this);
   }
}
