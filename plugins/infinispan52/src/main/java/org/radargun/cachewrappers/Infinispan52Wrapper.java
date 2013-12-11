package org.radargun.cachewrappers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.util.FileLookupFactory;
import org.radargun.config.DefaultConverter;
import org.radargun.features.DistributedTaskCapable;
import org.radargun.features.Queryable;
import org.radargun.features.XSReplicating;
import org.radargun.utils.ClassLoadHelper;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Michal Linhard (mlinhard@redhat.com)
 */
public class Infinispan52Wrapper extends Infinispan51Wrapper implements DistributedTaskCapable<Object>, Queryable, XSReplicating {

   protected List<Integer> slaves;
   protected boolean wrapForQuery;

   protected final InfinispanDistributedTask distributedTask;
   protected final InfinispanQueryable queryable;

   public Infinispan52Wrapper() {
      this.distributedTask = createDistributedTask();
      this.queryable = createQueryable();
   }

   @Override
   protected Infinispan52Lifecycle createLifecycle() {
      return new Infinispan52Lifecycle(this);
   }

   @Override
   protected Infinispan52MapReduce createMapReduce() {
      return new Infinispan52MapReduce(this);
   }

   @Override
   protected Infinispan52BasicOperations createBasicOperations() {
      return new Infinispan52BasicOperations(this);
   }

   @Override
   protected Infinispan51AtomicOperations createAtomicOperations() {
      return new Infinispan52AtomicOperations(this);
   }

   protected InfinispanDistributedTask createDistributedTask() {
      return new InfinispanDistributedTask(this);
   }

   protected InfinispanQueryable createQueryable() {
      return new InfinispanQueryable(this);
   }

   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws FileNotFoundException {
      InputStream input = FileLookupFactory.newInstance().lookupFileStrict(configFile, Thread.currentThread().getContextClassLoader());
      return new ParserRegistry(Thread.currentThread().getContextClassLoader()).parse(input);
   }

   @Override
   protected void setUpCaches() throws Exception {
      wrapForQuery = confAttributes.getBooleanProperty("wrapForQuery", false);

      // the site properties are selected in StartHelper
      String slaves = confAttributes.getProperty("slaves");
      if (slaves != null && !slaves.isEmpty()) {
         this.slaves = (List<Integer>) DefaultConverter.staticConvert(slaves, DefaultConverter.parametrized(List.class, Integer.class));
      }
      int siteIndex = confAttributes.getIntProperty("siteIndex", -1);
      if (siteIndex < 0) {
         log.info("Cannot find any site for slave index " + nodeIndex);
      } else {
         log.info("Slave " + nodeIndex + " will use site " + confAttributes.getProperty("siteName", "site[" + siteIndex + "]"));
      }

      super.setUpCaches();
      // config dumping
      if (confAttributes.getBooleanProperty("dumpConfig", false)) {
         String productName = confAttributes.getProperty("productName", "default");
         String configName = confAttributes.getProperty("configName", "default");
         File dumpDir = new File("conf" + File.separator + "normalized" + File.separator + productName + File.separator + configName);
         if (!dumpDir.exists()) {
            dumpDir.mkdirs();
         }
         dumpConfiguration(dumpDir);
      }
   }

   /**
    * 
    * Dump wrapper configuration as set of files into the dump directory (one per product/config)
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
            getMainCacheName());
      helper.dumpJGroups(new File(dumpDir, "config_jgroups.xml"), cacheManager.getCacheManagerConfiguration().transport() == null ? null : cacheManager
            .getCacheManagerConfiguration().transport().clusterName());
   }

   protected ConfigDumpHelper createConfigDumpHelper() {
      return new ConfigDumpHelper();
   }

   @Override
   protected String getKeyInfo(String bucket, Object key) {
      DistributionManager dm = getCache(bucket).getAdvancedCache().getDistributionManager();
      return super.getKeyInfo(bucket, key) + ", segmentId=" + dm.getConsistentHash().getSegment(key);
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
   public int getValueByteOverhead() {
      return 152;
   }

   @Override
   public String getMainCache() {
      return getCache(null).getName();
   }

   @Override
   public Collection<String> getBackupCaches() {
      Set<String> backupCaches = new HashSet<String>(cacheManager.getCacheNames());
      backupCaches.remove(getMainCache());
      return backupCaches;
   }

   @Override
   public List<Integer> getSlaves() {
      return slaves;
   }

   @Override
   public boolean isBridge() {
      return cacheManager.isCoordinator();
   }

   @Override
   public List<Future<Object>> executeDistributedTask(ClassLoadHelper classLoadHelper, String distributedCallableFqn,
                                      String executionPolicyName, String failoverPolicyFqn, String nodeAddress, Map<String, String> params) {
      return distributedTask.executeDistributedTask(classLoadHelper, distributedCallableFqn, executionPolicyName, failoverPolicyFqn, nodeAddress, params);
   }

   @Override
   public QueryResult executeQuery(Map<String, Object> queryParameters) {
      return queryable.executeQuery(queryParameters);
   }

   public Object wrapValue(Object value) {
      if (wrapForQuery) {
         return queryable.wrapForQuery(value);
      } else {
         return value;
      }
   }
}
