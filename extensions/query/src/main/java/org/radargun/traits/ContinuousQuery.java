package org.radargun.traits;

import org.radargun.Operation;

@Trait(doc = "Allows to listen to updates for specified query.")
public interface ContinuousQuery {

   public static final String TRAIT = ContinuousQuery.class.getSimpleName();
   public static final String QUERY = TRAIT + ".Query";
   public static final String LISTENERS = TRAIT + ".ContinuousQueryListeners";
   public final Operation ENTRY_JOINED = Operation.register(TRAIT + ".EntryJoined");
   public final Operation ENTRY_LEFT = Operation.register(TRAIT + ".EntryLeft");

   void createContinuousQuery(String cacheName, Query query, ContinuousQueryListener cqListener);

   void removeContinuousQuery(String cacheName, Object cqListener);

   interface ContinuousQueryListener<K, V> {
      void onEntryJoined(K key, V value);

      void onEntryLeft(K key);
   }

}
