package org.radargun.traits;

import java.util.Collection;
import java.util.Map;

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
       * @return Number of entries that are 'owned' by this node. Sum of all nodes' {@link #getOwnedSize()}
       * should be equal to {@link #getTotalSize()}. If this value is negative, this information is not available.
       */
      long getOwnedSize();

      /**
       * @return Number of entries physically stored on this cache node. Sum of all nodes' {@link #getLocallyStoredSize()}
       * should be equal to {@link #getTotalSize()} * {@link #getNumReplicas()}. If the entry is stored multiple times
       * (such as in-memory, on SSD disk and on conventional disk) it should be reported only once.
       * If this value is negative, this information is not available.
       */
      long getLocallyStoredSize();

      /**
       * @return Number of entries physically stored on this cache node in heap memory. Entries persisted into
       * disk or off-heap entries are not included.
       */
      long getMemoryStoredSize();

      /**
       * @return Number of entries in the whole cache, or negative number if the information is not available.
       */
      long getTotalSize();

      /**
       * The cache may be structured into different subparts.
       * @return Map of subpart-identification - subpart-size.
       */
      Map<?, Long> getStructuredSize();

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
