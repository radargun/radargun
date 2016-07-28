package org.radargun.traits;

@Trait(doc = "Conditional operations that are not propagated to other clustered nodes.")
public interface LocalConditionalOperations {
   <K, V> ConditionalOperations.Cache<K, V> getLocalCache(String cacheName);
}
