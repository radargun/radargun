package org.radargun.traits;

@Trait(doc = "Operations that read/modify only the in-memory state of a Cache.")
public interface InMemoryBasicOperations {
   <K, V> BasicOperations.Cache<K, V> getMemoryOnlyCache(String cacheName);
}
