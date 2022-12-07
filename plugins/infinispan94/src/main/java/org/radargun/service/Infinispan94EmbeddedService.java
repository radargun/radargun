package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.ProvidesTrait;

/**
 * Infinispan 9.4.x Embedded Service
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan94EmbeddedService extends Infinispan90EmbeddedService {

   @ProvidesTrait
   public InternalsExposition createInternalsExposition() {
      return new Infinispan94InternalsExposition(this);
   }
}