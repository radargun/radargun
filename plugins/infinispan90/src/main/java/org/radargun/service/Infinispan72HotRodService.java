package org.radargun.service;

import org.radargun.traits.ProvidesTrait;

/**
 * @author Matej Cimbora
 */
public abstract class Infinispan72HotRodService extends Infinispan71HotrodService {

   // Adds support for native getAll invocation
   @ProvidesTrait
   public Infinispan72HotRodOperations createInfinispan72HotRodOperations() {
      return new Infinispan72HotRodOperations(this);
   }

}
