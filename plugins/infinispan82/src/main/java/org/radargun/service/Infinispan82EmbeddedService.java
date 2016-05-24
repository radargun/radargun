package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.ProvidesTrait;

/**
 * @author vjuranek
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan82EmbeddedService extends Infinispan81EmbeddedService {

   @ProvidesTrait
   public ContinuousQuery createContinuousQuery() {
      return new Infinispan82EmbeddedContinuousQuery(this);
   }

}
