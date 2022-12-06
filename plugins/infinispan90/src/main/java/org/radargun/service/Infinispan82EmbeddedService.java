package org.radargun.service;

import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.ProvidesTrait;

/**
 * @author vjuranek
 */
public abstract class Infinispan82EmbeddedService extends Infinispan81EmbeddedService {

   @ProvidesTrait
   @Override
   public ContinuousQuery createContinuousQuery() {
      return new Infinispan82EmbeddedContinuousQuery(this);
   }

}
