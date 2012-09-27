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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.infinispan.Cache;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.JChannel;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.protocols.relay.Relayer;
import org.radargun.features.XSReplicating;
import org.radargun.utils.TypedProperties;

public class InfinispanXSWrapper extends InfinispanPartitionableWrapper implements XSReplicating {
   
   private static Log log = LogFactory.getLog(InfinispanWrapper.class);
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
            StringTokenizer tokenizer = new StringTokenizer(value, ",");   
            List<Integer> slaves = new ArrayList<Integer>();
            while (tokenizer.hasMoreTokens()) {
               try {
                  slaves.add(Integer.parseInt(tokenizer.nextToken().trim()));
               } catch (NumberFormatException e) {
                  log.debug("Cannot parse slave index from " + property + "=" + value);
               }
            }
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
      if (mySiteIndex < 0) {
         log.error("Cannot find any site for slave index " + nodeIndex);
         throw new IllegalArgumentException();
      } else {
         String siteName = confAttributes.getProperty("site[" + mySiteIndex + "].name", "site[" + mySiteIndex + "]");
         log.info("Slave " + nodeIndex + " will use site " + siteName);
      }
      
      String configFile = confAttributes.getProperty("site[" + mySiteIndex + "].config");
      String mainCacheName = confAttributes.getProperty("site[" + mySiteIndex + "].cache");
      
      log.trace("Using config file: " + configFile + " and cache name: " + mainCacheName);

      cacheManager = new DefaultCacheManager(configFile, false);
      preStartCacheManager();
      cacheManager.start();
      String cacheNames = cacheManager.getDefinedCacheNames();
      if (!cacheNames.contains(mainCacheName))
         throw new IllegalStateException("The requested cache(" + mainCacheName + ") is not defined. Defined cache " +
                                               "names are " + cacheNames);
      mainCache = cacheManager.getCache(mainCacheName);
      // Start also the other caches
      for (String cacheName : getBackupCaches()) {
         cacheManager.getCache(cacheName);
      }
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
   protected List<JChannel> getChannels() {
      List<JChannel> list = new ArrayList<JChannel>();
      JGroupsTransport transport = ((JGroupsTransport) cacheManager.getTransport());
      JChannel clusterChannel = (JChannel) transport.getChannel();
      list.add(clusterChannel);
      RELAY2 relay = (RELAY2) clusterChannel.getProtocolStack().findProtocol(RELAY2.class);
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
      return mainCache.getName();
   }

   @Override
   public Collection<String> getBackupCaches() {
      Set<String> backupCaches = new HashSet<String>(cacheManager.getCacheNames());
      backupCaches.remove(mainCache.getName());
      return backupCaches;
   }
   
   @Override
   public Cache<Object, Object> getCache(String name) {
      if (name == null) return mainCache;
      return cacheManager.getCache(name, false);
   }

   @Override
   public List<Integer> getSlaves() {
      return slaves;
   }
   
   @Override
   public boolean isBridge() {
      return cacheManager.isCoordinator();
   }
}
