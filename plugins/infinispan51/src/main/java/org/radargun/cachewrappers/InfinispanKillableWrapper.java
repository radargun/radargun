package org.radargun.cachewrappers;

import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.JChannel;
import org.jgroups.protocols.DISCARD;
import org.radargun.Killable;

/**
 * 
 * InfinispanWrapper that can kill the cache manager by cutting JGroups communication.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 */
public class InfinispanKillableWrapper extends InfinispanWrapper implements Killable {

   @Override
   public void kill() throws Exception {
      if (started) {
         JGroupsTransport transport = (JGroupsTransport) cacheManager.getTransport();
         JChannel channel = (JChannel) transport.getChannel();
         channel.getProtocolStack().addProtocol(new DISCARD());
         cacheManager.stop();
         started = false;
      }
   }

}
