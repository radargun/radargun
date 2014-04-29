package org.radargun.traits;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Conditional operations that are not propagated to other clustered nodes.")
public interface LocalConditionalOperations {
   <K, V> ConditionalOperations.Cache<K, V> getLocalCache(String cacheName);
}
