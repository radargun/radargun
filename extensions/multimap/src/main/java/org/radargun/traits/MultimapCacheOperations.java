package org.radargun.traits;

import java.util.Collection;
import java.util.function.Predicate;

import org.radargun.Operation;

@Trait(doc = "MultimapCache operations")
public interface MultimapCacheOperations {
   String TRAIT = MultimapCacheOperations.class.getSimpleName();

   Operation GET = Operation.register(TRAIT + ".Get");
   Operation PUT = Operation.register(TRAIT + ".Put");
   Operation REMOVE = Operation.register(TRAIT + ".Remove");
   Operation REMOVE_BY_KEY_VALUE = Operation.register(TRAIT + ".RemoveByKeyValue");
   Operation REMOVE_BY_PREDICATE = Operation.register(TRAIT + ".RemoveByPredicate");
   Operation CONTAINS_KEY = Operation.register(TRAIT + ".ContainsKey");
   Operation CONTAINS_VALUE = Operation.register(TRAIT + ".ContainsValue");
   Operation CONTAINS_ENTRY = Operation.register(TRAIT + ".ContainsEntry");
   Operation SIZE = Operation.register(TRAIT + ".Size");

   <K, V> MultimapCache<K, V> getMultimapCache(String multimapCacheName);

   String getCacheName();

   interface MultimapCache<K, V> {
      Collection<V> get(K key) throws Exception;

      Void put(K key, V value) throws Exception;

      Boolean remove(K key) throws Exception;

      Boolean remove(K key, V value) throws Exception;

      Void remove(Predicate<? super V> p) throws Exception;

      Boolean containsKey(K key) throws Exception;

      Boolean containsValue(V value) throws Exception;

      Boolean containsEntry(K key, V value) throws Exception;

      Long size() throws Exception;
   }
}
