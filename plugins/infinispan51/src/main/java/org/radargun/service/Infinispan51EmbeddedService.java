package org.radargun.service;

import java.io.IOException;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.TransactionConfiguration;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.Parser;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan51EmbeddedService extends InfinispanEmbeddedService {
   @Property(doc = "Explicitely lock each modification. Default is false.")
   protected boolean explicitLocking = false;

   @Property(doc = "Use batching instead of transactions. Default is false.")
   protected boolean batching = false;

   protected InfinispanPartitionableLifecycle partitionable;
   protected InfinispanTopologyHistory topologyAware;

   // rather dirty hack to replace KeyGeneratorAware
   private static Infinispan51EmbeddedService instance;

   static Infinispan51EmbeddedService getInstance() {
      return instance;
   }

   public Infinispan51EmbeddedService() {
      instance = this;
      partitionable = (InfinispanPartitionableLifecycle) lifecycle;
      topologyAware = createTopologyAware();
   }

   @Override
   protected InfinispanPartitionableLifecycle createLifecycle() {
      return new InfinispanPartitionableLifecycle(this);
   }

   @ProvidesTrait
   @Override
   public Infinispan51Operations createBasicOperations() {
      return new Infinispan51Operations(this);
   }

   @ProvidesTrait
   @Override
   public InfinispanTransactional createTransactional() {
      return new Infinispan51Transactional(this);
   }

   @ProvidesTrait
   public InfinispanBulkOperations createBulkOperations() {
      return new InfinispanBulkOperations(this);
   }

   @ProvidesTrait
   public InfinispanMapReduce createMapReduce() {
      return new InfinispanMapReduce(this);
   }

   @ProvidesTrait
   public InfinispanTopologyHistory getTopologyAware() {
      return topologyAware;
   }

   // topologyAware must be created only once and ASAP after setting up the cache
   protected InfinispanTopologyHistory createTopologyAware() {
      return new InfinispanTopologyHistory(this);
   }

   @Override
   protected void startCaches() throws Exception {
      super.startCaches();
      topologyAware.registerListener(getCache(null));
   }

   @Override
   protected DefaultCacheManager createCacheManager(String configFile) throws IOException {
      ConfigurationBuilderHolder cbh = createConfiguration(configFile);
      cbh.getGlobalConfigurationBuilder().transport().transport(partitionable.createTransport());
      DefaultCacheManager cm = new DefaultCacheManager(cbh, false);
      beforeCacheManagerStart(cm);
      cm.start();
      return cm;
   }

   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws IOException {
      return new Parser(Thread.currentThread().getContextClassLoader()).parseFile(configFile);
   }

   protected int membersCount(ConsistentHash consistentHash) {
      return consistentHash.getCaches().size();
   }

   @Override
   protected boolean isCacheDistributed(Cache<?, ?> cache) {
      return cache.getCacheConfiguration().clustering().cacheMode().isDistributed();
   }

   @Override
   protected boolean isCacheClustered(Cache<?, ?> cache) {
      return cache.getCacheConfiguration().clustering().cacheMode().isClustered();
   }

   @Override
   protected boolean isCacheTransactional(Cache<?, ?> cache) {
      TransactionConfiguration txConfig = cache.getCacheConfiguration().transaction();
      return txConfig != null && txConfig.transactionMode() == TransactionMode.TRANSACTIONAL;
   }

   protected boolean isCacheBatching(Cache<?, ?> cache) {
      Configuration config = cache.getCacheConfiguration();
      return config.invocationBatching().enabled();
   }

   @Override
   protected boolean isCacheAutoCommit(Cache<?, ?> cache) {
      TransactionConfiguration txConfig = cache.getCacheConfiguration().transaction();
      return txConfig != null && txConfig.autoCommit();
   }

   protected boolean isCachePessimistic(Cache<?, ?> cache) {
      TransactionConfiguration txConfig = cache.getCacheConfiguration().transaction();
      return txConfig != null && txConfig.lockingMode().equals(LockingMode.PESSIMISTIC);
   }

   public int getNumOwners(Cache<?, ?> cache) {
      switch (cache.getCacheConfiguration().clustering().cacheMode()) {
         case LOCAL: return 1;
         case REPL_SYNC:
         case REPL_ASYNC:
            return cacheManager.getMembers().size();
         case INVALIDATION_SYNC:
         case INVALIDATION_ASYNC:
         case DIST_SYNC:
         case DIST_ASYNC:
            return cache.getCacheConfiguration().clustering().hash().numOwners();
      }
      throw new IllegalStateException();
   }

   boolean isExplicitLocking(Cache<?, ?> cache) {
      if (explicitLocking) {
         if (isCachePessimistic(cache)) {
            return true;
         } else {
            log.warn("Cannot use explicit locking with optimistic transactions.");
            return false;
         }
      } else {
         return false;
      }
   }
}
