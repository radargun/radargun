package org.radargun.traits;

@Trait(doc = "Basic operations that are not propagated to other clustered nodes.")
public interface LocalBasicOperations {
   <K, V> BasicOperations.Cache<K, V> getLocalCache(String cacheName);
}
