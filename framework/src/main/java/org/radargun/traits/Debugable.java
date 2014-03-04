package org.radargun.traits;

/**
 * Feature for wrappers supporting debug info output.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Access to internal information about keys and the whole cache.")
public interface Debugable {

   Cache getCache(String cacheName);

   public interface Cache<K> {
      /**
       * Log debug info for particular key. An example of implementation could be
       * to enable full tracing and do a GET operation, print information on which
       * node should the key be located, where are the backups etc.
       */
      void debugKey(K key);

      /**
       * Log debug info about the whole cache.
       */
      void debugInfo();
   }
}
