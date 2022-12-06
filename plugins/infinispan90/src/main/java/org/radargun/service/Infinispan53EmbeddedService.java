package org.radargun.service;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class Infinispan53EmbeddedService extends Infinispan52EmbeddedService {

   @Override
   public abstract InfinispanCacheInfo createCacheInformation();
}
