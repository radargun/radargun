package org.radargun.service;

import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.protocols.TP;
import org.radargun.Service;

/**
 * @author Roman Macor (rmacor@redhat.com)
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan90EmbeddedService extends Infinispan82EmbeddedService {

   @Override
   protected Infinispan90Lifecycle createLifecycle() {
      return new Infinispan90Lifecycle(this);
   }

   @Override
   protected TP getTransportProtocol() {
      JGroupsTransport transport = (JGroupsTransport) cacheManager.getTransport();
      return (TP) transport.getChannel().getProtocolStack().findProtocol(TP.class);
   }

}