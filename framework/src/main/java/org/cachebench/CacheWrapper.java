package org.cachebench;


/**
 * CacheWrappers wrap cacheing products tp provide the cachebench framework with a standard way of
 * accessing and manipulating a cache.
 *
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: CacheWrapper.java,v 1.5 2007/05/17 08:13:45 msurtani Exp $
 */
public interface CacheWrapper
{
   /**
    * Initialises the cache.  Typically this step will configure the
    * cacheing product with various params passed in, described in
    * cachebench.xml for a particular cacheing product, which is
    * usually the name or path to a config file specific to the
    * cacheing product being tested.
    *
    * @param config
    */
   void init(String config) throws Exception;

   /**
    * setUp() is called immediately after init(), and usually involves instantiating
    * a cache.
    */
   void setUp() throws Exception;

   /**
    * This is called at the very end of all tests on this cache, and is used for clean-up
    * operations.
    */
   void tearDown() throws Exception;

   /**
    * This method is called when the framework needs to put an object in cache.  This method is treated
    * as a black box, and is what is timed, so it should be implemented in the most efficient (or most
    * realistic) way possible.
    *
    * @param bucket a bucket is a group of keys. Some implementations might ignore the bucket (e.g. {@link org.cachebench.cachewrappers.InfinispanWrapper}}
    * so in order to avoid key collisions, one should make sure that the keys are unique even between different buckets. 
    * @param key
    * @param value
    */
   void put(String bucket, Object key, Object value) throws Exception;

   /**
    * @see #put(String, Object, Object)
    */
   Object get(String bucket, Object key) throws Exception;

   /**
    * This is called after each test type (if emptyCacheBetweenTests is set to true in cachebench.xml) and is
    * used to flush the cache.
    */
   void empty() throws Exception;

   /**
    * @return the number of members in the cache's cluster
    */
   int getNumMembers();

   /**
    * @return Some info about the cache contents, perhaps just a count of objects.
    */
   String getInfo();

   /**
    * Some caches (e.g. JBossCache with  buddy replication) do not store replicated data directlly in the main
    * structure, but use some additional structure to do this (replication tree, in the case of buddy replication).
    * This method is a hook for handling this situations.
    */
   Object getReplicatedData(String bucket, String key) throws Exception;

   Object startTransaction();

   void endTransaction(boolean successful);

}
