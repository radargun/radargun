package org.radargun;


import org.radargun.utils.TypedProperties;

/**
 * CacheWrappers wrap caching products to provide RadarGun with a standard way of
 * accessing and manipulating a cache.
 *
 * @author Manik Surtani
 */
public interface CacheWrapper extends BasicOperations
{
   /**
    * Initialises the cache.  Typically this step will configure the
    * caching product with various params passed in, described in
    * benchmark.xml for a particular caching product, which is
    * usually the name or path to a config file specific to the
    * caching product being tested.
    *
    * @param config
    * @param nodeIndex
    * @param confAttributes
    */
   void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception;

   /**
    * This is called at the very end of all tests on this cache, and is used for clean-up
    * operations.
    */
   void tearDown() throws Exception;

   boolean isRunning();

   /**
    * @return the number of members in the cache's cluster
    */
   int getNumMembers();

   /**
    * @return Some info about the cache contents, perhaps just a count of objects.
    */
   String getInfo();

   /**
    * @param bucket
    * @return True if transactional operations could be performed on this cache.
    */
   boolean isTransactional(String bucket);

   /**
    * Starts a transaction against the cache node. All the put, get, empty invocations after this method returns will
    * take place in the scope of the transaction started. The transaction will be completed by invoking {@link #endTransaction(boolean)}.
    * @throws RuntimeException if a particular cache implementation does not support transactions it should throw a
    * RuntimeException to signal that.
    */
   void startTransaction();

   /**
    * Called in conjunction with {@link #startTransaction()} in order to complete a transaction by either committing or rolling it back.
    * @param successful commit or rollback?
    */
   void endTransaction(boolean successful);

   /**
    * @return number of entries on this cache node
    */
   public int getLocalSize();

   /**
    * @return number of entries int the whole cache
    */
   public int getTotalSize();

}
