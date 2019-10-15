package org.radargun.service;

import java.util.List;
import java.util.Set;

/**
 * POJO class to represent the response of GET /v2/cache-managers/{cacheManagerName}
 * @author Gustavo Lira &lt;glira@redhat.com&gt;
 */
public class CacheManagerInfo {

   private String coordinatorAddress, cacheManagerStatus, createdCacheCount, runningCacheCount;
   private String version, name, nodeAddress, physicalAddresses, clusterName, logicalAddressString;
   private boolean coordinator;
   private Set<BasicCacheInfo> definedCaches;
   private List<String> clusterMembers, clusterMembersPhysicalAddresses;
   private int clusterSize;


   public String getCoordinatorAddress() {
      return coordinatorAddress;
   }

   public boolean isCoordinator() {
      return coordinator;
   }

   public String getCacheManagerStatus() {
      return cacheManagerStatus;
   }

   public boolean isRunning() {
      if(this.getCacheManagerStatus() == null) {
         return false;
      }
      return this.getCacheManagerStatus().equalsIgnoreCase("RUNNING");
   }

   public Set<BasicCacheInfo> getDefinedCaches() {
      return definedCaches;
   }

   public String getCreatedCacheCount() {
      return createdCacheCount;
   }

   public String getRunningCacheCount() {
      return runningCacheCount;
   }

   public String getVersion() {
      return version;
   }

   public String getName() {
      return name;
   }

   public String getNodeAddress() {
      return nodeAddress;
   }

   public String getPhysicalAddresses() {
      return physicalAddresses;
   }

   public List<String> getClusterMembers() {
      return clusterMembers;
   }

   public List<String> getClusterMembersPhysicalAddresses() {
      return clusterMembersPhysicalAddresses;
   }

   public int getClusterSize() {
      return clusterSize;
   }

   public String getClusterName() {
      return clusterName;

   }

   private String getLogicalAddressString() {
      return logicalAddressString;
   }

   public void setCoordinatorAddress(String coordinatorAddress) {
      this.coordinatorAddress = coordinatorAddress;
   }

   public void setCacheManagerStatus(String cacheManagerStatus) {
      this.cacheManagerStatus = cacheManagerStatus;
   }

   public void setCreatedCacheCount(String createdCacheCount) {
      this.createdCacheCount = createdCacheCount;
   }

   public void setRunningCacheCount(String runningCacheCount) {
      this.runningCacheCount = runningCacheCount;
   }

   public void setVersion(String version) {
      this.version = version;
   }

   public void setName(String name) {
      this.name = name;
   }

   public void setNodeAddress(String nodeAddress) {
      this.nodeAddress = nodeAddress;
   }

   public void setPhysicalAddresses(String physicalAddresses) {
      this.physicalAddresses = physicalAddresses;
   }

   public void setClusterName(String clusterName) {
      this.clusterName = clusterName;
   }

   public void setLogicalAddressString(String logicalAddressString) {
      this.logicalAddressString = logicalAddressString;
   }

   public void setCoordinator(boolean coordinator) {
      this.coordinator = coordinator;
   }

   public void setDefinedCaches(Set<BasicCacheInfo> definedCaches) {
      this.definedCaches = definedCaches;
   }

   public void setClusterMembers(List<String> clusterMembers) {
      this.clusterMembers = clusterMembers;
   }

   public void setClusterMembersPhysicalAddresses(List<String> clusterMembersPhysicalAddresses) {
      this.clusterMembersPhysicalAddresses = clusterMembersPhysicalAddresses;
   }

   public void setClusterSize(int clusterSize) {
      this.clusterSize = clusterSize;
   }

   @Override
   public String toString() {
      return "CacheManagerInfo{" +
            "clusterMembers=" + clusterMembers +
            ", clusterSize=" + clusterSize +
            '}';
   }

   static class BasicCacheInfo {
      String name;
      boolean started;

      BasicCacheInfo() {

      }

      BasicCacheInfo(String name, boolean started) {
         this.name = name;
         this.started = started;
      }

      public String getName() {
         return name;
      }

      public boolean isStarted() {
         return started;
      }
   }

   static class ClusterMember {
      String name;
      String address;

      public ClusterMember(String name, String address) {
         this.name = name;
         this.address = address;
      }

      public String getName() {
         return name;
      }

      public String getAddress() {
         return address;
      }
   }
}
