package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.CounterOperations;
import org.radargun.traits.ProvidesTrait;

/**
 *
 * @author Martin Gencur
 */
@Service(doc = HazelcastService.SERVICE_DESCRIPTION)
public class Hazelcast39Service extends Hazelcast36Service {

   @ProvidesTrait
   public CounterOperations createCounterOperations() {
      return new org.radargun.service.Hazelcast39CounterOperations(this);
   }
}
