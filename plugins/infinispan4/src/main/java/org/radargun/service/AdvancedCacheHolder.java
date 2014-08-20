package org.radargun.service;

import org.infinispan.AdvancedCache;

/**
 * Denotes that this objects holds an instance of AdvancedCache
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface AdvancedCacheHolder {
   AdvancedCache getAdvancedCache();
}
