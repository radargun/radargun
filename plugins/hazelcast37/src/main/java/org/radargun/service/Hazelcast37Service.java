package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

@Service(doc = "Hazelcast")
public class Hazelcast37Service extends Hazelcast36Service {
   @ProvidesTrait
   public Hazelcast37AsyncOperations createAsyncOperations() {
      return new Hazelcast37AsyncOperations(this);
   }
}
