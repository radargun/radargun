package org.radargun;

/**
 * Operations that must be implemented in all CacheWrappers. These methods are treated as a black boxes,
 * and are used for benchmarking. Therefore, these should be implemented in the most efficient (or most
 * realistic) way possible.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface BasicOperations {
   /**
    * This method is called when the framework needs to put an object in cache.
    *
    * @param bucket a bucket is a group of keys. Multiple buckets can target to single cache,
    *               depending on implementation and configuration.
    * @param key
    * @param value
    */
   void put(String bucket, Object key, Object value) throws Exception;

   /**
    * @see #put(String, Object, Object)
    */
   Object get(String bucket, Object key) throws Exception;

   /**
    * Some caches (e.g. JBossCache with  buddy replication) do not store replicated data directlly in the main
    * structure, but use some additional structure to do this (replication tree, in the case of buddy replication).
    * This method is a hook for handling this situations.
    */
   Object getReplicatedData(String bucket, String key) throws Exception;

   /**
    * @see #put(String, Object, Object)
    */
   Object remove(String bucket, Object key) throws Exception;

   /**
    * Remove all entries from the cache. If local is set to true, remove entries only on this node.
    * @param local
    */
   void clear(boolean local) throws Exception;
}
