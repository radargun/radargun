package org.radargun.stages.cache.background;

import org.radargun.config.Property;
import org.radargun.utils.TimeConverter;

/**
 * Configuration options shared by all background stressor logics
 */
public class GeneralConfiguration {
   @Property(doc = "Ratio of PUT requests. Default is 1.")
   protected int puts = 1;

   @Property(doc = "Ratio of GET requests. Default is 2.")
   protected int gets = 2;

   @Property(doc = "Ratio of REMOVE requests. Default is 0.")
   protected int removes = 0;

   @Property(doc = "Number of entries (key-value pairs) inserted into the cache. Default is 1024. Needs to be greater " +
      "than or equal to the product of 'numThreads' and group size.")
   protected long numEntries = 1024;

   @Property(doc = "First key ID used for key generation. Default is 0.")
   protected long keyIdOffset = 0;

   @Property(doc = "Number of stressor threads. Default is 10.")
   protected int numThreads = 10;

   @Property(doc = "Number of requests wrapped into single transaction. By default transactions are not used (explicitly).")
   protected int transactionSize = -1;

   @Property(converter = TimeConverter.class, doc = "Time between consecutive requests of one stressor thread. Default is 0.")
   protected long delayBetweenRequests = 0;

   @Property(doc = "Period after which a slave is considered to be dead. Default is 90 s.", converter = TimeConverter.class)
   protected long deadSlaveTimeout = 90000;

   @Property(doc = "By default each thread accesses only its private set of keys. This allows all threads all values. " +
      "Atomic operations are required for this functionality. Default is false.")
   protected boolean sharedKeys = false;

   @Property(doc = "Cache used for the background operations. Default is null (default).")
   protected String cacheName;

   public int getPuts() {
      return puts;
   }

   public int getGets() {
      return gets;
   }

   public int getRemoves() {
      return removes;
   }

   public long getNumEntries() {
      return numEntries;
   }

   public int getNumThreads() {
      return numThreads;
   }

   public int getTransactionSize() {
      return transactionSize;
   }

   public long getDelayBetweenRequests() {
      return delayBetweenRequests;
   }

   public long getDeadSlaveTimeout() {
      return deadSlaveTimeout;
   }

   public boolean isSharedKeys() {
      return sharedKeys;
   }

   public String getCacheName() {
      return cacheName;
   }
}
