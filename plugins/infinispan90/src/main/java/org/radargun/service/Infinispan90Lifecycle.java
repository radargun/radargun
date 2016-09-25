package org.radargun.service;

import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.JChannel;

/**
 * @author Roman Macor (rmacor@redhat.com)
 */
public class Infinispan90Lifecycle extends Infinispan52Lifecycle {

   public Infinispan90Lifecycle(Infinispan90EmbeddedService service) {
      super(service);
   }

   @Override
   protected JGroupsTransport getTransport() {
      return (JGroupsTransport) (service.cacheManager).getTransport();
   }

   @Override
   protected JChannel getTransportChannels() {
      return (JChannel) transport.getChannel();
   }
}