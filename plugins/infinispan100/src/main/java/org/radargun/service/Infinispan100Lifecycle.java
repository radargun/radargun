package org.radargun.service;

import org.infinispan.remoting.transport.Transport;

/**
 * Infinispan 10.0.x Life cycle
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public class Infinispan100Lifecycle extends Infinispan90Lifecycle {

   public Infinispan100Lifecycle(Infinispan100EmbeddedService service) {
      super(service);
   }

   public Transport createTransport() {
      return new Infinispan100HookedJGroupsTransport(this);
   }
}