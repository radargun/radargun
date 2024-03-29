package org.radargun.service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.TransactionConfiguration;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import org.jgroups.JChannel;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class Infinispan51EmbeddedService extends InfinispanEmbeddedService {
   @Property(doc = "Explicitely lock each modification. Default is false.")
   protected boolean explicitLocking = false;

   @Property(doc = "Use batching instead of transactions. Default is false.")
   protected boolean batching = false;

   @Property(doc = "List of caches that should be removed prior to CacheManager.stop() - workarounds issues when stopping the server.")
   protected List<String> removedCaches = Collections.EMPTY_LIST;

   protected InfinispanPartitionableLifecycle partitionable;
   protected InfinispanTopologyHistory topologyAware;

   // stores channels used in JGroups for cleanup after unsuccessful shutdown
   protected Set<JChannel> jgroupsChannels = new HashSet<>();

   public Infinispan51EmbeddedService() {
      partitionable = (InfinispanPartitionableLifecycle) lifecycle;
      topologyAware = createTopologyAware();
   }

   @ProvidesTrait
   @Override
   public InfinispanOperations createBasicOperations() {
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
      for (Cache cache : caches.values()) {
         topologyAware.registerListener(cache);
      }
      jgroupsChannels.addAll(partitionable.getChannels());
   }

   @Override
   protected void stopCaches() {
      try {
         for (String removedCache : removedCaches) {
            try {
               ((DefaultCacheManager) cacheManager).removeCache(removedCache);
            } catch (Exception e) {
               log.error("Failed to remove cache " + removedCache, e);
            }
         }
         super.stopCaches();
      } finally {
         topologyAware.reset();
      }
   }

   @Override
   protected void forcedCleanup() {
      for (JChannel channel : jgroupsChannels) {
         try {
            channel.close();
         } catch (Exception e) {
            log.error("Failed to close channel " + channel.getName(), e);
         }
      }
      jgroupsChannels.clear();
      super.forcedCleanup();
   }

   @Override
   protected DefaultCacheManager createCacheManager(String configFile) throws IOException {
      ConfigurationBuilderHolder cbh = createConfiguration(configFile);
      cbh.getGlobalConfigurationBuilder().transport().transport(partitionable.createTransport());
      DefaultCacheManager cm = new DefaultCacheManager(cbh, false);
      beforeCacheManagerStart(cm);
      return cm;
   }

   protected abstract ConfigurationBuilderHolder createConfiguration(String configFile) throws IOException;

   protected int membersCount(ConsistentHash consistentHash) {
      throw new IllegalStateException("As part of the plugins cleanup this method is not supported");
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
         case LOCAL:
            return 1;
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

   protected boolean isExplicitLocking(Cache<?, ?> cache) {
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
