package org.radargun.service;

/**
 * @author Gustavo Lira &lt;glira@redhat.com&gt;
 */
public class Infinispan100ServerClustered extends InfinispanServerClustered {

   private InfinispanRestAPI restAPI;
   protected CacheManagerInfo cacheManagerInfo;

   public Infinispan100ServerClustered(Infinispan100ServerService service, Integer defaultPort) {
      super(service);
      this.restAPI = new InfinispanRestAPI(defaultPort);
   }

   @Override
   public boolean isCoordinator() {
      return cacheManagerInfo != null && cacheManagerInfo.isCoordinator();
   }

   protected InfinispanCacheManagerInfo getInfinispanServerClustered() {
      InfinispanCacheManagerInfo infinispanCacheManagerInfo = null;
      this.cacheManagerInfo = restAPI.getCacheManager();
      if (this.cacheManagerInfo != null && this.cacheManagerInfo.getName() != null) {
         String membersString = cacheManagerInfo.getClusterMembers().toString();
         String nodeAddress = cacheManagerInfo.getNodeAddress();
         infinispanCacheManagerInfo = new InfinispanCacheManagerInfo(membersString, nodeAddress);
      }
      return infinispanCacheManagerInfo;
   }
}
