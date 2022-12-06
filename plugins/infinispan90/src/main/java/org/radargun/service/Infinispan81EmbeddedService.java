package org.radargun.service;

import org.radargun.traits.ContinuousQuery;

/**
 * @author vjuranek
 */
public abstract class Infinispan81EmbeddedService extends Infinispan80EmbeddedService {

   public abstract ContinuousQuery createContinuousQuery();

}
