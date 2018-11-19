package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.ProvidesTrait;

/**
 * Infinispan 10.0.x Embedded Service
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan100EmbeddedService extends Infinispan92EmbeddedService {

   @ProvidesTrait
   public InternalsExposition createInternalsExposition() {
      return new Infinispan100InternalsExposition(this);
   }
}