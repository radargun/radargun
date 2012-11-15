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

import org.infinispan.Cache;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.util.FileLookupFactory;
import org.jgroups.JChannel;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.protocols.relay.Relayer;
import org.radargun.features.XSReplicating;
import org.radargun.utils.TypedProperties;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InfinispanXSWrapper extends InfinispanPartitionableWrapper implements XSReplicating {
   
   private Cache<Object, Object> mainCache;
   private List<Integer> slaves;
   
   @Override
   protected void setUpCache(TypedProperties confAttributes, int nodeIndex) throws Exception {
      Pattern slavesPattern = Pattern.compile("site\\[(\\d*)\\].slaves");
      Matcher m;
      int mySiteIndex = -1;
      for (String property : confAttributes.stringPropertyNames()) {
         if ((m = slavesPattern.matcher(property)).matches()) {
            String value = confAttributes.getProperty(property);
            slaves = org.radargun.stages.helpers.ParseHelper.parseList(value, property, log);
            if (slaves.contains(nodeIndex)) {
               try {
                  mySiteIndex = Integer.parseInt(m.group(1));
                  this.slaves = slaves;
                  break;
               } catch (NumberFormatException e) {
                  log.debug("Cannot parse site index from " + property);
               }
            }
         }
      }

      String configFile, mainCacheName;
      if (mySiteIndex < 0) {
         log.info("Cannot find any site for slave index " + nodeIndex);
         configFile = getConfigFile(confAttributes);
         mainCacheName = getCacheName(confAttributes);
      } else {
         String siteName = confAttributes.getProperty("site[" + mySiteIndex + "].name", "site[" + mySiteIndex + "]");
         log.info("Slave " + nodeIndex + " will use site " + siteName);
         configFile = confAttributes.getProperty("site[" + mySiteIndex + "].config");
         mainCacheName = confAttributes.getProperty("site[" + mySiteIndex + "].cache");
      }

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
            List<?> bridges = (List<?>) bridgesField.get(relayer);
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
         log.warn("No RELAY2 protocol in XS wrapper!");
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
         cacheManager.getCache(cache, false).clear();
      }
   }
}
