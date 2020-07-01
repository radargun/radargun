package org.radargun.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.infinispan.manager.DefaultCacheManager;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanServerClustered implements Clustered {
   protected final Log log = LogFactory.getLog(getClass());
   protected final InfinispanServerService service;
   protected final List<Membership> membershipHistory = new ArrayList<>();
   protected String lastMembers = null;

   public InfinispanServerClustered(InfinispanServerService service) {
      this.service = service;
      service.schedule(new Runnable() {
         @Override
         public void run() {
            checkAndUpdateMembership();
         }
      }, service.viewCheckPeriod);
   }

   @Override
   public boolean isCoordinator() {
      if (!service.lifecycle.isRunning()) {
         return false;
      }
      try {
         MBeanServerConnection connection = service.connection;
         if (connection == null)
            return false;
         ObjectName cacheManagerName = getCacheManagerObjectName(service.jmxDomain, service.cacheManagerName);
         String nodeAddress = (String) connection.getAttribute(cacheManagerName, getNodeAddressAttribute());
         String clusterMembers = (String) connection.getAttribute(cacheManagerName, getClusterMembersAttribute());
         return clusterMembers.startsWith("[" + nodeAddress);
      } catch (Exception e) {
         log.error("Failed to retrieve data from JMX", e);
      }
      return false;
   }

   protected Collection<Member> checkAndUpdateMembership() {
      if (!service.lifecycle.isRunning()) {
         synchronized (this) {
            if (!membershipHistory.isEmpty()
                  && membershipHistory.get(membershipHistory.size() - 1).members.size() > 0) {
               lastMembers = null;
               membershipHistory.add(Membership.empty());
            }
         }
         return Collections.emptyList();
      }
      try {
         InfinispanCacheManagerInfo infinispanCacheManagerInfo = getInfinispanServerClustered();
         if (infinispanCacheManagerInfo != null) {
            String membersString = infinispanCacheManagerInfo.getMembersString();
            String nodeAddress = infinispanCacheManagerInfo.getNodeAddress();
            synchronized (this) {
               if (lastMembers != null && lastMembers.equals(membersString)) {
                  return membershipHistory.get(membershipHistory.size() - 1).members;
               }
               String[] nodes;
               if (!membersString.startsWith("[") || !membersString.endsWith("]")) {
                  nodes = new String[] { membersString };
                  log.infof("single node: %s", Arrays.toString(nodes));
               } else {
                  nodes = membersString.substring(1, membersString.length() - 1).split(",", 0);
                  log.infof("complex nodes: %s", Arrays.toString(nodes));
               }
               lastMembers = membersString;

               ArrayList<Member> members = new ArrayList<>();
               for (int i = 0; i < nodes.length; ++i) {
                  members.add(new Member(nodes[i].trim(), nodes[i].equals(nodeAddress), i == 0));
               }
               membershipHistory.add(Membership.create(members));
               return members;
            }
         } else {
            return Collections.emptyList();
         }
      } catch (Exception e) {
         log.error("Failed to retrieve data", e);
         return null;
      }
   }

   protected InfinispanCacheManagerInfo getInfinispanServerClustered() {
      InfinispanCacheManagerInfo infinispanServerClustered = null;
      try {
         MBeanServerConnection connection = service.connection;
         if (connection != null) {
            ObjectName cacheManagerName = getCacheManagerObjectName(service.jmxDomain, service.cacheManagerName);
            if (cacheManagerName != null) {
               String membersString = (String) connection.getAttribute(cacheManagerName, getClusterMembersAttribute());
               String nodeAddress = (String) connection.getAttribute(cacheManagerName, getNodeAddressAttribute());
               infinispanServerClustered = new InfinispanCacheManagerInfo(membersString, nodeAddress);
            }
         }
         return infinispanServerClustered;
      } catch (IOException | JMException e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public Collection<Member> getMembers() {
      return checkAndUpdateMembership();
   }

   @Override
   public synchronized List<Membership> getMembershipHistory() {
      return new ArrayList<>(membershipHistory);
   }

   protected String getClusterMembersAttribute() {
      return "clusterMembers";
   }

   protected String getNodeAddressAttribute() {
      return "nodeAddress";
   }

   protected String getClusterSizeAttribute() {
      return "clusterSize";
   }

   private ObjectName getCacheManagerObjectName(String jmxDomain, String managerName) throws IOException, JMException {
      // Find CacheManager MBean using parameters, if that fails use type 
      ObjectName cacheManagerName = new ObjectName(
            String.format("%s:type=CacheManager,name=\"%s\",component=CacheManager", jmxDomain, managerName));
      try {
         service.connection.getMBeanInfo(cacheManagerName);
      } catch (InstanceNotFoundException | IntrospectionException | ReflectionException e) {
         cacheManagerName = null;
         log.error(String.format(
               "Failed to find CacheManager MBean using domain '%s' and name '%s'. Trying again using type.", jmxDomain,
               managerName));
         Set<ObjectInstance> cacheManagers = service.connection.queryMBeans(null, javax.management.Query
               .isInstanceOf(javax.management.Query.value(DefaultCacheManager.class.getTypeName())));
         if (cacheManagers.size() == 0) {
            log.error("Failed to find CacheManager MBean using domain, name, or type.", e);
            throw e;
         } else {
            cacheManagerName = cacheManagers.iterator().next().getObjectName();
         }
      }
      log.info(String.format("Found CacheManager '%s' MBean.", cacheManagerName));
      return cacheManagerName;
   }
}
