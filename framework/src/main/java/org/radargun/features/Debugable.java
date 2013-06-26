package org.radargun.features;

/**
 * Feature for wrappers supporting debug info output.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Debugable {
   /**
    * Log debug info for particular key. An example of implementation could be
    * to enable full tracing and do a GET operation, print information on which
    * node should the key be located, where are the backups etc.
    * @param bucket
    * @param key
    */
   void debugKey(String bucket, Object key);

   /**
    * Log debug info about the whole cache.
    * @param bucket
    */
   void debugInfo(String bucket);
}
