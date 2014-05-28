package org.radargun.traits;

import java.util.Collection;
import java.util.Map;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Information about the Cache.")
public interface CacheInformation {
   /**
    * @return Unique identifier of the cache we usually get through getCache(null) on other traits.
    */
   String getDefaultCacheName();

   /**
    * @return Unique identifiers of all caches.
    */
   Collection<String> getCacheNames();

   Cache getCache(String cacheName);

   interface Cache {
      /**
       * @return Number of entries on this cache node.
       */
      int getLocalSize();

      /**
       * @return Number of entries in the whole cache, or negative number if the information is not available.
       */
      int getTotalSize();

      /**
       * The cache may be structured into different subparts.
       * @return Map of subpart-identification - subpart-size.
       */
      Map<?, Integer> getStructuredSize();

      /**
       * @return How many times is each entry replicated in the local cluster (group), or -1 if this cannot be determined.
       */
      int getNumReplicas();

      /**
       * @return Approximate number of bytes above the key and value size needed to store
       *         into the cache, or negative number if the information is not available.
       */
      int getEntryOverhead();
   }
}
