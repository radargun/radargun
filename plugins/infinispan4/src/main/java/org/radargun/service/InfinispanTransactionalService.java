package org.radargun.service;

/**
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public interface InfinispanTransactionalService {

   boolean isEnlistExtraXAResource();

   boolean isCacheTransactional(String cacheName);

   boolean isBatching();

   boolean isCacheBatching(String cacheName);
}
