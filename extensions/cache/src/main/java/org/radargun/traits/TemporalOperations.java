package org.radargun.traits;

import org.radargun.Operation;

/**
 * @author mgencur
 */
@Trait(doc = "Operations that use lifespan or maxIdle parameters.")
public interface TemporalOperations {

    String TRAIT = TemporalOperations.class.getSimpleName();
    Operation PUT_WITH_LIFESPAN = Operation.register(TRAIT + ".PutWithLifespan");
    Operation GET_AND_PUT_WITH_LIFESPAN = Operation.register(TRAIT + ".GetAndPutWithLifespan");
    Operation PUT_WITH_LIFESPAN_AND_MAXIDLE = Operation.register(TRAIT + ".PutWithLifespanAndMaxIdle");
    Operation GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE = Operation.register(TRAIT + ".GetAndPutWithLifespanAndMaxIdle");
    Operation PUT_IF_ABSENT_WITH_LIFESPAN = Operation.register(TRAIT + ".PutIfAbsentWithLifespan");
    Operation PUT_IF_ABSENT_WITH_LIFESPAN_AND_MAXIDLE = Operation.register(TRAIT + ".PutIfAbsentWithLifespanAndMaxIdle");

    <K, V> Cache<K, V> getCache(String name);

    interface Cache<K, V> {

        /**
         * Associates the specified value with the specified key in this cache.
         *
         * @param key      key to use
         * @param value    value to store
         * @param lifespan lifespan of the entry in milliseconds. Negative values are interpreted as unlimited lifespan.
         */
        void put(K key, V value, long lifespan);

        /**
         * Associates the specified value with the specified key in this cache.
         *
         * @param key      key to use
         * @param value    value to store
         * @param lifespan lifespan of the entry in milliseconds. Negative values are interpreted as unlimited lifespan.
         * @return the value being replaced, or null if nothing is being replaced.
         */
        V getAndPut(K key, V value, long lifespan);

        /**
         * Associates the specified value with the specified key in this cache if there's currently no value
         * associated with the key.
         *
         * @param key      key to use
         * @param value    value to store
         * @param lifespan lifespan of the entry in milliseconds.  Negative values are interpreted as unlimited lifespan.
         * @return true if the value was successfully associated with the key, or false if the key had a previously
         * associated value
         */
        boolean putIfAbsent(K key, V value, long lifespan);

        /**
         * Associates the specified value with the specified key in this cache.
         *
         * @param key             key to use
         * @param value           value to store
         * @param lifespan        lifespan of the entry in milliseconds.  Negative values are interpreted as unlimited lifespan.
         * @param maxIdleTime     the maximum amount of time this key is allowed to be idle for before it is considered as
         *                        expired (in milliseconds)
         */
        void put(K key, V value, long lifespan, long maxIdleTime);

        /**
         * Associates the specified value with the specified key in this cache.
         *
         * @param key             key to use
         * @param value           value to store
         * @param lifespan        lifespan of the entry in milliseconds.  Negative values are interpreted as unlimited lifespan.
         * @param maxIdleTime     the maximum amount of time this key is allowed to be idle for before it is considered as
         *                        expired (in milliseconds)
         * @return the value being replaced, or null if nothing is being replaced.
         */
        V getAndPut(K key, V value, long lifespan, long maxIdleTime);

        /**
         * Associates the specified value with the specified key in this cache if there's currently no value
         * associated with the key.
         *
         * @param key             key to use
         * @param value           value to store
         * @param lifespan        lifespan of the entry in milliseconds.  Negative values are interpreted as unlimited lifespan.
         * @param maxIdleTime     the maximum amount of time this key is allowed to be idle for before it is considered as
         *                        expired (in milliseconds)
         * @return true if the value was successfully associated with the key, or false if the key had a previously
         * associated value
         */
        boolean putIfAbsent(K key, V value, long lifespan, long maxIdleTime);
    }


}
