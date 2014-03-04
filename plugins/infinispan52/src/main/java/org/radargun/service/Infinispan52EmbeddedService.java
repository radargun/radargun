package org.radargun.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Set;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.util.FileLookupFactory;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Michal Linhard (mlinhard@redhat.com)
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan52EmbeddedService extends Infinispan51EmbeddedService {

   @Property(doc = "Wrap values inserted into cache as queryable objects. Default is false.")
   protected boolean wrapForQuery = false;

   @Property(doc = "Dump configuration into property files. Default is false.")
   protected boolean dumpConfig = false;

   @Property(name = Service.PROP_PLUGIN, doc = "Name of the current product.", optional = false)
   protected String plugin;

   @Property(name = Service.PROP_CONFIG_NAME, doc = "Name of the current config.", optional = false)
   protected String configName;

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
   public Infinispan52Operations createBasicOperations() {
      return new Infinispan52Operations(this);
   }

   @ProvidesTrait
   @Override
   public InfinispanCacheInfo createCacheInformation() {
      return new Infinispan52CacheInfo(this);
   }

   @ProvidesTrait
   public InfinispanDistributedTask createDistributedTask() {
      return new InfinispanDistributedTask(this);
   }

   @ProvidesTrait
   public InfinispanQueryable createQueryable() {
      return new InfinispanQueryable(this);
   }

   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws FileNotFoundException {
      InputStream input = FileLookupFactory.newInstance().lookupFileStrict(configFile, Thread.currentThread().getContextClassLoader());
      return new ParserRegistry(Thread.currentThread().getContextClassLoader()).parse(input);
   }

   @Override
   protected void startCaches() throws Exception {
      super.startCaches();
      // config dumping
      if (dumpConfig) {
         File dumpDir = new File("conf" + File.separator + "normalized" + File.separator + plugin + File.separator + configName);
         if (!dumpDir.exists()) {
            dumpDir.mkdirs();
         }
         dumpConfiguration(dumpDir);
      }
   }

   /**
    * 
    * Dump service configuration as set of files into the dump directory (one per product/config)
    * 
    * @param dumpDir
    */
   protected void dumpConfiguration(File dumpDir) {
      ConfigDumpHelper helper = createConfigDumpHelper();
      if (!dumpDir.exists()) {
         dumpDir.mkdirs();
      }
      helper.dumpGlobal(new File(dumpDir, "config_global.xml"), cacheManager.getCacheManagerConfiguration(), cacheManager.getName());
      helper.dumpCache(new File(dumpDir, "config_cache.xml"), getCache(null).getAdvancedCache().getCacheConfiguration(), cacheManager.getName(),
            getCache(null).getName());
      helper.dumpJGroups(new File(dumpDir, "config_jgroups.xml"), cacheManager.getCacheManagerConfiguration().transport() == null ? null : cacheManager
            .getCacheManagerConfiguration().transport().clusterName());
   }

   protected ConfigDumpHelper createConfigDumpHelper() {
      return new ConfigDumpHelper();
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

   public Object wrapValue(Object value) {
      return InfinispanQueryable.wrapForQuery(value);
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
