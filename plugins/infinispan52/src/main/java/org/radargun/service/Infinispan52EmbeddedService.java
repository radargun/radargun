package org.radargun.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hibernate.search.spi.SearchFactoryIntegrator;
import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.infinispan.util.FileLookupFactory;
import org.radargun.Service;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan52EmbeddedService extends Infinispan51EmbeddedService {

   protected InfinispanDistributedTask distributedTaskExecutor;

   @Property(doc = "Classes to add to search factory after service has started.")
   protected List<String> preregisteredQueryClasses;

   @Override
   protected Infinispan52Lifecycle createLifecycle() {
      return new Infinispan52Lifecycle(this);
   }

   @ProvidesTrait
   @Override
   public Infinispan52MapReduce createMapReduce() {
      return new Infinispan52MapReduce(this);
   }

   @ProvidesTrait
   @Override
   public InfinispanCacheInfo createCacheInformation() {
      return new Infinispan52CacheInfo(this);
   }

   @ProvidesTrait
   public InfinispanDistributedTask getDistributedTaskExecutor() {
      return distributedTaskExecutor;
   }

   @ProvidesTrait
   public EmbeddedConfigurationProvider createConfigurationProvider() {
      return new EmbeddedConfigurationProvider(this);
   }

   @Init
   public void init() {
       distributedTaskExecutor = new InfinispanDistributedTask(this);
   }

   @Override
   protected void startCaches() throws Exception {
      super.startCaches();
      if (preregisteredQueryClasses != null) {
         SearchManager searchManager = Search.getSearchManager(getCache(null));
         SearchFactoryIntegrator sfi = (SearchFactoryIntegrator) searchManager.getSearchFactory();
         List<Class> classList = new ArrayList<>(preregisteredQueryClasses.size());
         for (String clazz : preregisteredQueryClasses) {
            classList.add(Thread.currentThread().getContextClassLoader().loadClass(clazz));
         }
         sfi.addClasses(classList.toArray(new Class[classList.size()]));
      }
   }

   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws FileNotFoundException {
      ClassLoader classLoader = getClass().getClassLoader();
      try (InputStream input = FileLookupFactory.newInstance().lookupFileStrict(configFile, classLoader)) {
         return new ParserRegistry(classLoader).parse(input);
      } catch (IOException e) {
         log.error("Failed to get configuration input stream", e);
      }
      return null;
   }

   protected ConfigDumpHelper createConfigDumpHelper() {
      return new ConfigDumpHelper();
   }

   @Override
   protected String getJmxDomain() {
      return ((DefaultCacheManager) cacheManager).getCacheManagerConfiguration().globalJmxStatistics().domain();
   }

   @Override
   protected String getKeyInfo(AdvancedCache cache, Object key) {
      DistributionManager dm = cache.getDistributionManager();
      return super.getKeyInfo(cache, key) + ", segmentId=" + dm.getConsistentHash().getSegment(key);
   }

   @Override
   protected String getCHInfo(DistributionManager dm) {
      StringBuilder sb = new StringBuilder(1000);
      sb.append("\nWrite CH: ").append(dm.getWriteConsistentHash());
      sb.append("\nRead CH: ").append(dm.getReadConsistentHash());
      return sb.toString();
   }

   @Override
   protected String toString(InternalCacheEntry ice) {
      if (ice == null) return null;
      StringBuilder sb = new StringBuilder(256);
      sb.append(ice.getClass().getSimpleName());
      sb.append("[key=").append(ice.getKey()).append(", value=").append(ice.getValue());
      sb.append(", created=").append(ice.getCreated()).append(", isCreated=").append(ice.isCreated());
      sb.append(", lastUsed=").append(ice.getLastUsed()).append(", isChanged=").append(ice.isChanged());
      sb.append(", expires=").append(ice.getExpiryTime()).append(", isExpired=").append(ice.isExpired());
      sb.append(", canExpire=").append(ice.canExpire()).append(", isEvicted=").append(ice.isEvicted());
      sb.append(", isRemoved=").append(ice.isRemoved()).append(", isValid=").append(ice.isValid());
      sb.append(", lifespan=").append(ice.getLifespan()).append(", maxIdle=").append(ice.getMaxIdle());
      sb.append(", version=").append(ice.getVersion()).append(", lockPlaceholder=").append(ice.isLockPlaceholder());
      return sb.append(']').toString();
   }

   @Override
   protected int membersCount(ConsistentHash consistentHash) {
      return consistentHash.getMembers().size();
   }

   @Override
   protected boolean isJoinComplete(Cache<?, ?> cache) {
      DistributionManager dm = cache.getAdvancedCache().getDistributionManager();
      boolean joinComplete = dm.isJoinComplete();
      Set<Integer> ownedSegments = dm.getReadConsistentHash().getSegmentsForOwner(cache.getCacheManager().getAddress());
      if (log.isTraceEnabled()) {
         log.trace("joinComplete=" + joinComplete + ", ownedSegments=" + ownedSegments + ", " + getCHInfo(dm));
      }
      return joinComplete && !ownedSegments.isEmpty();
   }
}
