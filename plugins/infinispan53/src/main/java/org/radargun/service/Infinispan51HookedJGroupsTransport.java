package org.radargun.service;

import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.JChannel;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

public class Infinispan51HookedJGroupsTransport extends JGroupsTransport {

   protected final Log log = LogFactory.getLog(getClass());

   private final InfinispanPartitionableLifecycle lifecycle;

   public Infinispan51HookedJGroupsTransport(InfinispanPartitionableLifecycle lifecycle) {
      this.lifecycle = lifecycle;
   }

   /**
    * This is called after the channel is initialized but before it is connected
    */
   @Override
   protected void startJGroupsChannelIfNeeded() {
      this.lifecycle.handleStartJGroupsChannelIfNeeded((JChannel) this.channel);
      super.startJGroupsChannelIfNeeded();
   }
}