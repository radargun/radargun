package org.radargun.cachewrappers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.TransactionConfiguration;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.Parser;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import org.radargun.config.Property;
import org.radargun.features.BulkOperationsCapable;
import org.radargun.features.KeyGeneratorAware;
import org.radargun.features.Killable;
import org.radargun.features.MapReduceCapable;
import org.radargun.features.Partitionable;
import org.radargun.features.PersistentStorageCapable;
import org.radargun.features.TopologyAware;
import org.radargun.stressors.KeyGenerator;
import org.radargun.utils.ClassLoadHelper;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan51Wrapper extends InfinispanWrapper
      implements BulkOperationsCapable, KeyGeneratorAware, Killable, MapReduceCapable, Partitionable, PersistentStorageCapable, TopologyAware {
   @Property(doc = "Explicitely lock each modification. Default is false.")
   protected boolean explicitLocking = false;

   @Property(doc = "Use batching instead of transactions. Default is false.")
   protected boolean batching = false;

   protected final InfinispanPartitionableLifecycle partitionable;
   protected final InfinispanBulkOperations bulkOperations;
   protected final InfinispanKeyGeneratorAware keyGeneratorAware;
   protected final InfinispanMapReduce mapReduce;
   protected InfinispanPersistentStorage persistentStorage;
   protected InfinispanTopologyAware topologyAware;

   public Infinispan51Wrapper() {
      this.bulkOperations = createBulkOperations();
      this.keyGeneratorAware = createKeyGeneratorAware();
      this.mapReduce = createMapReduce();
      this.partitionable = (InfinispanPartitionableLifecycle) lifecycle;
   }

   @Override
   protected InfinispanPartitionableLifecycle createLifecycle() {
      return new InfinispanPartitionableLifecycle(this);
   }

   @Override
   protected Infinispan51BasicOperations createBasicOperations() {
      return new Infinispan51BasicOperations(this);
   }

   @Override
   protected Infinispan51AtomicOperations createAtomicOperations() {
      return new Infinispan51AtomicOperations(this);
   }

   protected InfinispanBulkOperations createBulkOperations() {
      return new InfinispanBulkOperations(this);
   }

   protected InfinispanKeyGeneratorAware createKeyGeneratorAware() {
      return new InfinispanKeyGeneratorAware(this);
   }

   protected InfinispanMapReduce createMapReduce() {
      return new InfinispanMapReduce(this);
   }

   protected InfinispanPersistentStorage createPersistentStorage() {
      return new InfinispanPersistentStorage(this);
   }

   protected InfinispanTopologyAware createTopologyAware() {
      return new InfinispanTopologyAware(this);
   }

   public boolean isClusterValidationRequest(String bucket) {
      return bucket.startsWith("clusterValidation") ? true : false;
   }

   public boolean isExplicitLocking() {
      return explicitLocking;
   }

   @Override
   protected void setUpCaches() throws Exception {
      super.setUpCaches();
      topologyAware = createTopologyAware();
      persistentStorage = createPersistentStorage();
      setUpExplicitLocking(getCache(null));
   }

   protected void setUpExplicitLocking(Cache<Object, Object> cache) {
      if (explicitLocking) {
         LockingMode lockingMode = cache.getAdvancedCache().getCacheConfiguration().transaction().lockingMode();
         if (lockingMode.equals(LockingMode.PESSIMISTIC)) {
            log.info("Using explicit locking!");
         } else {
            explicitLocking = false;
            log.error("Cannot use explicit locking with optimistic transactions.");
         }
      }
   }

   @Override
   public void startTransaction() {
      if (batching) {
         getCache(null).getAdvancedCache().startBatch();
      } else {
         super.startTransaction();
      }
   }

   @Override
   public void endTransaction(boolean successful) {
      if (batching) {
         getCache(null).getAdvancedCache().endBatch(successful);
      } else {
         super.endTransaction(successful);
      }
   }

   @Override
   protected DefaultCacheManager createCacheManager(String configFile) throws IOException {
      ConfigurationBuilderHolder cbh = createConfiguration(configFile);
      cbh.getGlobalConfigurationBuilder().transport().transport(partitionable.createTransport());
      return new DefaultCacheManager(cbh, true);
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
      TransactionConfiguration txConfig = ((DefaultCacheManager) cacheManager).getCacheConfiguration(cache.getName()).transaction();
      return txConfig != null && txConfig.transactionMode() == TransactionMode.TRANSACTIONAL;
   }

   @Override
   protected boolean isCacheAutoCommit(Cache<?, ?> cache) {
      TransactionConfiguration txConfig = ((DefaultCacheManager) cacheManager).getCacheConfiguration(cache.getName()).transaction();
      return txConfig != null && txConfig.autoCommit();
   }

   @Override
   public Map<Object, Object> getAll(String bucket, Set<Object> keys, boolean preferAsync) throws Exception {
      return bulkOperations.getAll(bucket, keys, preferAsync);
   }

   @Override
   public Map<Object, Object> putAll(String bucket, Map<Object, Object> entries, boolean preferAsync) throws Exception {
      return bulkOperations.putAll(bucket, entries, preferAsync);
   }

   @Override
   public Map<Object, Object> removeAll(String bucket, Set<Object> keys, boolean preferAsync) throws Exception {
      return bulkOperations.removeAll(bucket, keys, preferAsync);
   }

   @Override
   public KeyGenerator getKeyGenerator(int keyBufferSize) {
      return keyGeneratorAware.getKeyGenerator(keyBufferSize);
   }

   @Override
   public void kill() throws Exception {
      partitionable.kill();
   }

   @Override
   public void killAsync() throws Exception {
      partitionable.killAsync();
   }

   @Override
   public Object executeMapReduceTask(ClassLoadHelper classLoadHelper, String mapperFqn, String reducerFqn, String collatorFqn) throws Exception {
      return mapReduce.executeMapReduceTask(classLoadHelper, mapperFqn, reducerFqn, collatorFqn);
   }

   @Override
   public Map executeMapReduceTask(ClassLoadHelper classLoadHelper, String mapperFqn, String reducerFqn) throws Exception {
      return mapReduce.executeMapReduceTask(classLoadHelper, mapperFqn, reducerFqn);
   }

   @Override
   public boolean setDistributeReducePhase(boolean distributeReducePhase) {
      return mapReduce.setDistributeReducePhase(distributeReducePhase);
   }

   @Override
   public boolean setUseIntermediateSharedCache(boolean useIntermediateSharedCache) {
      return mapReduce.setUseIntermediateSharedCache(useIntermediateSharedCache);
   }

   @Override
   public boolean setTimeout(long timeout, TimeUnit unit) {
      return mapReduce.setTimeout(timeout, unit);
   }

   @Override
   public boolean setCombiner(String combinerFqn) {
      return mapReduce.setCombiner(combinerFqn);
   }

   @Override
   public void setParameters(Map mapperParameters, Map reducerParameters, Map combinerParameters, Map collatorParameters) {
      mapReduce.setParameters(mapperParameters, reducerParameters, combinerParameters, collatorParameters);
   }

   @Override
   public void setMembersInPartition(int slaveIndex, Set<Integer> members) {
      partitionable.setMembersInPartition(slaveIndex, members);
   }

   @Override
   public void setStartWithReachable(int slaveIndex, Set<Integer> members) {
      partitionable.setStartWithReachable(slaveIndex, members);
   }

   @Override
   public Object getMemoryOnly(String bucket, Object key) throws Exception {
      return persistentStorage.getMemoryOnly(bucket, key);
   }

   @Override
   public Object putMemoryOnly(String bucket, Object key, Object value) throws Exception {
      return persistentStorage.putMemoryOnly(bucket, key, value);
   }

   @Override
   public Object removeMemoryOnly(String bucket, Object key) throws Exception {
      return persistentStorage.removeMemoryOnly(bucket, key);
   }

   @Override
   public List<Event> getTopologyChangeHistory() {
      return topologyAware.getTopologyChangeHistory();
   }

   @Override
   public List<Event> getRehashHistory() {
      return topologyAware.getRehashHistory();
   }

   @Override
   public boolean isCoordinator() {
      return topologyAware.isCoordinator();
   }
}
