package org.radargun.service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public class Infinispan60ServerTopologyHistory extends AbstractTopologyHistory {
   protected final InfinispanServerService service;

   protected final static String ALL_CACHES = "__all_caches__";

   protected final static String JMX_CACHE_COMPONENT = "%s:type=Cache,name=\"*\",manager=\"clustered\",component=%s";
   // Topology change strings
   protected final static String JMX_RPC_MANAGER = "RpcManager";
   protected final static String JMX_PENDING_VIEW_ATTR = "pendingViewAsString";
   // Rehash strings
   protected final static String JMX_STATE_TRANSFER_MANAGER = "StateTransferManager";
   protected final static String JMX_STATE_TRANSFER_IN_PROGRESS_ATTR = "stateTransferInProgress";

   protected final Map<String, CacheStatus> cacheChangesOngoing = new HashMap<>();

   protected Set<ObjectName> stateTransferManagerNames = Collections.emptySet();
   Map<String, String> rehashCacheNameMap;

   protected Set<ObjectName> rpcManagerNames = Collections.emptySet();
   Map<String, String> topologyChangeCacheNameMap;

   public Infinispan60ServerTopologyHistory(InfinispanServerService service) {
      this.service = service;

      service.schedule(new Runnable() {
         @Override
         public void run() {
            try {
               processCacheStatus();
            } catch (Throwable e) {
               log.error("Checking cache status failed", e);
            }
         }
      }, service.viewCheckPeriod);
      service.lifecycle.addListener(new ProcessLifecycle.ListenerAdapter() {
         @Override
         public void afterStop(boolean graceful) {
            reset();
            cacheChangesOngoing.clear();
            stateTransferManagerNames.clear();
            rehashCacheNameMap = null;
            rpcManagerNames.clear();
            topologyChangeCacheNameMap = null;
            log.debug("Infinispan60ServerTopologyHistory.afterStop");
         }
      });
   }

   @Override
   protected String getDefaultCacheName() {
      return ALL_CACHES;
   }

   /**
    * Process the results from the map returned by {@link #cacheStatus()} and compare to the current
    * state. The map has a key for the cache name and a boolean value for rehash and topology
    * changes on a cache. If the new value for the cache doesn't match the current value, add an
    * event. If there is no current value then add it.
    */
   protected void processCacheStatus() {
      Map<String, CacheStatus> newResult = cacheStatus();
      for (Map.Entry<String, CacheStatus> entry : newResult.entrySet()) {
         if (cacheChangesOngoing.containsKey(entry.getKey())) {
            if (entry.getValue().rehashInProgress && !cacheChangesOngoing.get(entry.getKey()).rehashInProgress) {
               addEvent(hashChanges, entry.getKey(), true, 0, 0);
            }
            if (!entry.getValue().rehashInProgress && cacheChangesOngoing.get(entry.getKey()).rehashInProgress) {
               addEvent(hashChanges, entry.getKey(), false, 0, 0);
            }
            if (entry.getValue().topologyChangeInProgress
                  && !cacheChangesOngoing.get(entry.getKey()).topologyChangeInProgress) {
               addEvent(topologyChanges, entry.getKey(), true, 0, 0);
            }
            if (!entry.getValue().topologyChangeInProgress
                  && cacheChangesOngoing.get(entry.getKey()).topologyChangeInProgress) {
               addEvent(topologyChanges, entry.getKey(), false, 0, 0);
            }

         } else {
            if (entry.getValue().rehashInProgress) {
               addEvent(hashChanges, entry.getKey(), true, 0, 0);
            } else {
               addEvent(hashChanges, entry.getKey(), false, 0, 0);
            }
            if (entry.getValue().topologyChangeInProgress) {
               addEvent(topologyChanges, entry.getKey(), true, 0, 0);
            } else {
               addEvent(topologyChanges, entry.getKey(), false, 0, 0);
            }
         }
         cacheChangesOngoing.put(entry.getKey(), entry.getValue());
      }
   }

   /**
    * Uses JMX attributes to determine if a topology change or a rehash is occurring on a cache.
    * 
    * The <code>pendingViewAsString</code> attribute of the RpcManager component of each defined
    * cache determines if a topology change is in progress on this cache. If
    * <code>pendingViewAsString</code> equals <code>null</code>, no topology change is in progress.
    * 
    * The <code>stateTransferInProgress</code> attribute of the StateTransferManager component of
    * each defined cache determines if a rehash is in progress on this cache. If
    * <code>stateTransferInProgress</code> is <code>true</code>, a rehash is in progress.
    * 
    * @return a Map where the key is the cache name, and the value is a {@link CacheStatus} object.
    *         Also includes an entry for {@link #ALL_CACHES}.
    */
   protected Map<String, CacheStatus> cacheStatus() {
      Map<String, CacheStatus> statusMap = new HashMap<>();
      int rehashesInProgress = 0;
      int topologyChangesInProgress = 0;
      try {
         MBeanServerConnection connection = service.connection;
         if (connection == null)
            return statusMap;

         // Check for rehash
         if (stateTransferManagerNames.isEmpty()) {
            try {
               stateTransferManagerNames = connection.queryNames(
                     new ObjectName(String.format(JMX_CACHE_COMPONENT, service.jmxDomain, JMX_STATE_TRANSFER_MANAGER)),
                     null);
               rehashCacheNameMap = new HashMap<>();
               for (ObjectName stateTransferManagerName : stateTransferManagerNames) {
                  // Parse out cache name from ObjectName
                  String fullCacheName = ObjectName.unquote(stateTransferManagerName.getKeyProperty("name"));
                  String cacheName = fullCacheName.substring(0, fullCacheName.indexOf('('));
                  rehashCacheNameMap.put(stateTransferManagerName.toString(), cacheName);
               }
            } catch (IOException e) {
               // Server isn't started yet?
               log.debug("Couldn't query StateTransferManager object names.", e);
            }
         }
         for (ObjectName stateTransferManagerName : stateTransferManagerNames) {
            CacheStatus status = new CacheStatus();
            Boolean stateTransferInProgress = (Boolean) connection.getAttribute(stateTransferManagerName,
                  JMX_STATE_TRANSFER_IN_PROGRESS_ATTR);
            if (stateTransferInProgress) {
               log.debug("Rehash in progress for cache: " + rehashCacheNameMap.get(stateTransferManagerName.toString()));
               status.rehashInProgress = true;
               rehashesInProgress++;
            } else {
               log.debug("No rehash in progress");
               status.rehashInProgress = false;
            }
            statusMap.put(rehashCacheNameMap.get(stateTransferManagerName.toString()), status);
         }
         // Keep track of all cache rehashes
         CacheStatus status = new CacheStatus();
         if (rehashesInProgress == 0) {
            status.rehashInProgress = false;
         } else {
            status.rehashInProgress = true;
         }
         statusMap.put(ALL_CACHES, status);

         // Check for topology changes
         if (rpcManagerNames.isEmpty()) {
            try {
               rpcManagerNames = connection.queryNames(
                     new ObjectName(String.format(JMX_CACHE_COMPONENT, service.jmxDomain, JMX_RPC_MANAGER)), null);
               topologyChangeCacheNameMap = new HashMap<>();
               for (ObjectName rpcManagerName : rpcManagerNames) {
                  // Parse out cache name from ObjectName
                  String fullCacheName = ObjectName.unquote(rpcManagerName.getKeyProperty("name"));
                  String cacheName = fullCacheName.substring(0, fullCacheName.indexOf('('));
                  topologyChangeCacheNameMap.put(rpcManagerName.toString(), cacheName);
               }
            } catch (IOException e) {
               // Server isn't started yet?
               log.debug("Couldn't query RpcManager object names.", e);
            }
         }
         for (ObjectName rpcManagerName : rpcManagerNames) {
            status = statusMap.get(topologyChangeCacheNameMap.get(rpcManagerName.toString()));
            String pendingView = (String) connection.getAttribute(rpcManagerName, JMX_PENDING_VIEW_ATTR);
            if (pendingView.equals("null")) {
               log.debug("No topology change in progress");
               status.topologyChangeInProgress = false;
            } else {
               log.info("Topology change in progress. Pending view = " + pendingView);
               status.topologyChangeInProgress = true;
               topologyChangesInProgress++;
            }
            statusMap.put(topologyChangeCacheNameMap.get(rpcManagerName.toString()), status);
         }
         // Keep track of all cache topology changes
         status = statusMap.get(ALL_CACHES);
         if (topologyChangesInProgress == 0) {
            status.topologyChangeInProgress = false;
         } else {
            status.topologyChangeInProgress = true;
         }
         statusMap.put(ALL_CACHES, status);
      } catch (Exception e) {
         log.error("Failed to retrieve data from JMX", e);
      }
      return statusMap;
   }

   private static class CacheStatus {
      boolean rehashInProgress;
      boolean topologyChangeInProgress;
   }
}
