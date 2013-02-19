/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.cachewrappers;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.TransactionConfiguration;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.util.FileLookupFactory;
import org.jgroups.JChannel;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.protocols.relay.Relayer;
import org.radargun.config.DefaultConverter;
import org.radargun.features.XSReplicating;
import org.radargun.utils.TypedProperties;

public class InfinispanXSWrapper extends InfinispanPartitionableWrapper implements XSReplicating {
   
   private Cache<Object, Object> mainCache;
   private List<Integer> slaves;
   
   @Override
   protected void setUpCache(TypedProperties confAttributes, int nodeIndex) throws Exception {
      String slaves = confAttributes.getProperty("slaves");
      if (slaves != null && !slaves.isEmpty()) {
         this.slaves = (List<Integer>) DefaultConverter.staticConvert(slaves, DefaultConverter.parametrized(List.class, Integer.class));
      }
      String siteIndexString = confAttributes.getProperty("siteIndex");
      int siteIndex = -1;
      if (siteIndexString != null && !siteIndexString.isEmpty()) {
         try {
            siteIndex = Integer.parseInt(siteIndexString);
         } catch (NumberFormatException unused) {
            log.warn("Failed to parse site index");
         }
      }

      String configFile, mainCacheName;
      if (siteIndex < 0) {
         log.info("Cannot find any site for slave index " + nodeIndex);

      } else {
         log.info("Slave " + nodeIndex + " will use site " + confAttributes.getProperty("siteName", "site[" + siteIndex + "]"));
      }
      configFile = getConfigFile(confAttributes);
      mainCacheName = getCacheName(confAttributes);

      log.trace("Using config file: " + configFile + " and cache name: " + mainCacheName);

      cacheManager = createCacheManager(configFile);
      String cacheNames = cacheManager.getDefinedCacheNames();
      if (mainCacheName == null) {
         log.info("No main cache, only backups");
      } else {
         if (!cacheNames.contains(mainCacheName))
            throw new IllegalStateException("The requested cache(" + mainCacheName + ") is not defined. Defined cache names are " + cacheNames);
         mainCache = cacheManager.getCache(mainCacheName);
      }
      // Start also the other caches
      for (String cacheName : getBackupCaches()) {
         cacheManager.getCache(cacheName);
      }
   }

   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws FileNotFoundException {
      InputStream input = FileLookupFactory.newInstance().lookupFileStrict(configFile, Thread.currentThread().getContextClassLoader());
      return new ParserRegistry(Thread.currentThread().getContextClassLoader()).parse(input);
   }
   
   @Override
   protected void waitForRehash(TypedProperties confAttributes) throws InterruptedException {
      for (String cacheName : cacheManager.getCacheNames()) {
         Cache<Object, Object> cache = cacheManager.getCache(cacheName);
         blockForRehashing(cache);
         injectEvenConsistentHash(cache, confAttributes);
      }
   }
   
   @Override
   protected List<JChannel> getChannels(JChannel parentChannel, boolean failOnNotReady) {
      List<JChannel> list;
      if (parentChannel == null) {
         list = super.getChannels(null, failOnNotReady);
      } else {
         list = new ArrayList<JChannel>();
         list.add(parentChannel);
      }
      if (list.size() == 0) {
         log.info("No JGroups channels available");
         return list;
      }
      RELAY2 relay = (RELAY2) list.get(0).getProtocolStack().findProtocol(RELAY2.class);
      if (relay != null) {
         try {
            Field relayerField = RELAY2.class.getDeclaredField("relayer");
            relayerField.setAccessible(true);
            Relayer relayer = (Relayer) relayerField.get(relay);
            if (relayer == null) {
               log.debug("No relayer found");
               return list;
            }
            Field bridgesField = Relayer.class.getDeclaredField("bridges");
            bridgesField.setAccessible(true);
            Collection<?> bridges = (Collection<?>) bridgesField.get(relayer);
            if (bridges == null) {
               return list;
            }
            Field channelField = null;
            for (Object bridge : bridges) {
               if (channelField == null) {
                  channelField = bridge.getClass().getDeclaredField("channel");
                  channelField.setAccessible(true);                  
               }
               JChannel bridgeChannel = (JChannel) channelField.get(bridge);
               if (bridgeChannel.isOpen()) {
                  list.add(bridgeChannel);
               }
            }
         } catch (Exception e) {
            log.error("Failed to get channel from RELAY2 protocol", e);
         }
      } else {
         log.info("No RELAY2 protocol in XS wrapper");
      }
      return list;
   }

   @Override
   public String getMainCache() {
      if (mainCache == null) return null;
      return mainCache.getName();
   }

   @Override
   public Collection<String> getBackupCaches() {
      Set<String> backupCaches = new HashSet<String>(cacheManager.getCacheNames());
      if (mainCache != null) {
         backupCaches.remove(mainCache.getName());
      }
      return backupCaches;
   }
   
   @Override
   public Cache<Object, Object> getCache(String name) {
      if (name == null) return mainCache;
      Cache<Object, Object> cache = ((DefaultCacheManager) cacheManager).getCache(name, false);
      // fallback to the main cache to keep backward compatibility
      return cache != null ? cache : mainCache;
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
   public void empty() {
      for (String cache : cacheManager.getCacheNames()) {
         TransactionConfiguration txConfig = cacheManager.getCacheConfiguration(cache).transaction();
         boolean needsTx = (txConfig != null && txConfig.transactionMode() == TransactionMode.TRANSACTIONAL && !txConfig.autoCommit());
         Cache<Object, Object> c = cacheManager.getCache(cache, false);
         if (c == null) continue;
         try {
            if (needsTx) {
               c.getAdvancedCache().getTransactionManager().begin();
            }
            c.clear();
            if (needsTx) {
               c.getAdvancedCache().getTransactionManager().commit();
            }
         } catch (Exception e) {
            throw new RuntimeException("Failed to clear cache " + cache, e);
         }
      }
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
}
