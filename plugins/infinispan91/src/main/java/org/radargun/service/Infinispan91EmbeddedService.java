package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.CounterOperations;
import org.radargun.traits.ProvidesTrait;

@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan91EmbeddedService extends Infinispan90EmbeddedService {

   @ProvidesTrait
   public CounterOperations createCounterOperations() {
      return new Infinispan91CounterOperations(this);
   }

}