package org.radargun.service;

import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Infinispan 10.0.x custom JGroupsTransport
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public class Infinispan100HookedJGroupsTransport extends JGroupsTransport {

   private static final Log log = LogFactory.getLog(Infinispan100HookedJGroupsTransport.class);

   private InfinispanPartitionableLifecycle lifecycle;

   public Infinispan100HookedJGroupsTransport(InfinispanPartitionableLifecycle lifecycle) {
      this.lifecycle = lifecycle;
   }

   /**
    * This is called after the channel is initialized but before it is connected.
    * This code was copied from {@link Infinispan51HookedJGroupsTransport}. There is no code compatibility between 5.1 and 10.0 and we cannot extend it
    */
   @Override
   protected void startJGroupsChannelIfNeeded() {
      this.lifecycle.handleStartJGroupsChannelIfNeeded(this.channel);
      super.startJGroupsChannelIfNeeded();
      log.info(String.format("JGroups stack: %s", this.channel.getProtocolStack().getProtocols()));
   }
}
