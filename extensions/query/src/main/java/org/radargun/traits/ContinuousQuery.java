package org.radargun.traits;

import org.radargun.Operation;

@Trait(doc = "Allows to listen to updates for specified query.")
public interface ContinuousQuery {

   public static final String TRAIT = ContinuousQuery.class.getSimpleName();
   public static final String QUERY = TRAIT + ".Query";
   public static final String LISTENERS = TRAIT + ".ContinuousQueryListeners";
   public final Operation ENTRY_JOINED = Operation.register(TRAIT + ".EntryJoined");
   public final Operation ENTRY_LEFT = Operation.register(TRAIT + ".EntryLeft");

   ListenerReference createContinuousQuery(String cacheName, Query query, Listener cqListener);

   void removeContinuousQuery(String cacheName, ListenerReference listenerReference);

   interface Listener<K, V> {
      void onEntryJoined(K key, V value);

      void onEntryLeft(K key);
   }

   /**
    * Marker interface for implementation-specific data.
    */
   interface ListenerReference {}

}
