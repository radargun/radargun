package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.radargun.Service;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = "HotRod client")
public class Infinispan60HotrodService {

   @Property(doc = "List of server addresses the clients should connect to, separated by semicolons (;).")
   protected String servers;

   protected RemoteCacheManager manager;

   @Init
   public void init() {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      if (servers != null) {
         builder.addServers(servers);
      }
      manager = new RemoteCacheManager(builder.build(), false);
   }

   @ProvidesTrait
   public HotRodOperations createOperations() {
      return new HotRodOperations(this);
   }
}
