package org.radargun.service;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanServerClustered implements Clustered {
   protected final Log log = LogFactory.getLog(getClass());
   protected final InfinispanServerService service;

   public InfinispanServerClustered(InfinispanServerService service) {
      this.service = service;
   }

   @Override
   public boolean isCoordinator() {
      if (!service.lifecycle.isRunning()) {
         return false;
      }
      try {
         MBeanServerConnection connection = service.connection;
         if (connection == null) return false;
         ObjectName cacheManagerName = new ObjectName(getCacheManagerObjectName(service.jmxDomain, service.cacheManagerName));
         String nodeAddress = (String) connection.getAttribute(cacheManagerName, getNodeAddressAttribute());
         String clusterMembers = (String) connection.getAttribute(cacheManagerName, getClusterMembersAttribute());
         return clusterMembers.startsWith("[" + nodeAddress);
      } catch (Exception e) {
         log.error("Failed to retrieve data from JMX", e);
      }
      return false;
   }

   @Override
   public int getClusteredNodes() {
      if (!service.lifecycle.isRunning()) {
         return 0;
      }
      try {
         MBeanServerConnection connection = service.connection;
         if (connection == null) return 0;
         ObjectName cacheManagerName = new ObjectName(getCacheManagerObjectName(service.jmxDomain, service.cacheManagerName));
         return (Integer) connection.getAttribute(cacheManagerName, getClusterSizeAttribute());
      } catch (Exception e) {
         log.error("Failed to retrieve data from JMX", e);
      }
      return 0;
   }

   protected String getClusterMembersAttribute() {
      return "clusterManager";
   }

   protected String getNodeAddressAttribute() {
      return "nodeAddress";
   }

   protected String getClusterSizeAttribute() {
      return "clusterSize";
   }

   private String getCacheManagerObjectName(String jmxDomain, String managerName) {
      return String.format("%s:type=CacheManager,name=\"%s\",component=CacheManager", jmxDomain, managerName);
   }
}
