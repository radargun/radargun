package org.radargun.service;

import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.JChannel;
import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.TP;
import org.jgroups.stack.ProtocolStack;

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

   @Override
   protected void insertProtocol(JChannel channel, DISCARD discard) throws Exception {

      channel.getProtocolStack().insertProtocol(discard, ProtocolStack.Position.ABOVE, TP.class);
   }
}