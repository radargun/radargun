package org.radargun.service;

import org.radargun.Service;

/**
 * Infinispan 10.0.x Embedded Service
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan100EmbeddedService extends Infinispan94EmbeddedService {

   @Override
   protected Infinispan100Lifecycle createLifecycle() {
      return new Infinispan100Lifecycle(this);
   }
}