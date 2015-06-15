package org.radargun.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
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
   protected final Map<String, Map<ObjectName, String>> objectNameToCacheNames = new HashMap<>();

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
            objectNameToCacheNames.clear();
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
            }
            if (entry.getValue().topologyChangeInProgress) {
               addEvent(topologyChanges, entry.getKey(), true, 0, 0);
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
         Map<String, Object> cacheAttributes = this.retrieveJMXAttributeValues(connection,
               String.format(JMX_CACHE_COMPONENT, service.jmxDomain, JMX_STATE_TRANSFER_MANAGER),
               JMX_STATE_TRANSFER_IN_PROGRESS_ATTR);
         for (Map.Entry<String, Object> entry : cacheAttributes.entrySet()) {
            CacheStatus status = new CacheStatus();
            if ((Boolean) entry.getValue()) {
               log.debug("Rehash in progress on cache: " + entry.getKey());
               status.rehashInProgress = true;
               rehashesInProgress++;
            } else {
               log.trace("No rehash in progress on cache: " + entry.getKey());
               status.rehashInProgress = false;
            }
            statusMap.put(entry.getKey(), status);
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
         cacheAttributes = this.retrieveJMXAttributeValues(connection,
               String.format(JMX_CACHE_COMPONENT, service.jmxDomain, JMX_RPC_MANAGER), JMX_PENDING_VIEW_ATTR);
         for (Map.Entry<String, Object> entry : cacheAttributes.entrySet()) {
            status = statusMap.get(entry.getKey());
            if (((String) entry.getValue()).equals("null")) {
               log.trace("No topology change in progress on cache: " + entry.getKey());
               status.topologyChangeInProgress = false;
            } else {
               log.debug("Topology change in progress on cache: " + entry.getKey() + ". Pending view = "
                     + entry.getValue());
               status.topologyChangeInProgress = true;
               topologyChangesInProgress++;
            }
            statusMap.put(entry.getKey(), status);
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

   /**
    * Retrieve the specified attribute from the specified manager for every cache defined
    * 
    * @param connection
    *           the connection to the MBeanServer
    * @param mbeanQueryString
    *           A query string to return the ObjectName for each MBean associated with a cache
    * @param attrName
    *           the name of the attribute
    * @return a Map where the key is the cache name, and the value is the specified attribute
    */
   protected Map<String, Object> retrieveJMXAttributeValues(MBeanServerConnection connection, String mbeanQueryString,
         String attrName) {
      Map<String, Object> result = new HashMap<>();
      if (!objectNameToCacheNames.containsKey(mbeanQueryString)) {
         try {
            Set<ObjectName> objectNames = connection.queryNames(new ObjectName(mbeanQueryString), null);
            HashMap<ObjectName, String> cacheNameMap = new HashMap<>();
            for (ObjectName objectName : objectNames) {
               // Parse out cache name from ObjectName
               String fullCacheName = ObjectName.unquote(objectName.getKeyProperty("name"));
               String cacheName = fullCacheName.substring(0, fullCacheName.indexOf('('));
               cacheNameMap.put(objectName, cacheName);
            }
            objectNameToCacheNames.put(mbeanQueryString, cacheNameMap);
         } catch (IOException e) {
            // Server isn't started yet?
            log.debug("Couldn't query " + mbeanQueryString + " object names.", e);
         } catch (MalformedObjectNameException e) {
            log.debug("ObjectName " + mbeanQueryString + " is malformed", e);
         }
      }

      Map<ObjectName, String> obj2CacheName = objectNameToCacheNames.get(mbeanQueryString);
      for (ObjectName objectName : obj2CacheName.keySet()) {
         try {
            result.put(obj2CacheName.get(objectName), connection.getAttribute(objectName, attrName));
         } catch (Exception e) {
            log.debug("Failed to retrieve attribute: " + attrName + " for cache " + obj2CacheName.get(objectName), e);
         }
      }

      return result;
   }

   private static class CacheStatus {
      boolean rehashInProgress;
      boolean topologyChangeInProgress;
   }
}