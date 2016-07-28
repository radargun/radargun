package org.radargun.service;

import org.infinispan.AdvancedCache;

/**
 * Denotes that this objects holds an instance of AdvancedCache
 */
public interface AdvancedCacheHolder {
   AdvancedCache getAdvancedCache();
}
