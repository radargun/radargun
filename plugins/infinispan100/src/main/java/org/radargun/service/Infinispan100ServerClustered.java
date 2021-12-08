package org.radargun.service;

import java.io.IOException;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * @author Gustavo Lira &lt;glira@redhat.com&gt;
 */
public class Infinispan100ServerClustered extends InfinispanServerClustered {

   protected final Log log = LogFactory.getLog(getClass());

   private InfinispanRestAPI restAPI;
   private boolean coordinator;

   public Infinispan100ServerClustered(Infinispan100ServerService service, Integer defaultPort, String username, String password) throws IOException {
      super(service);
      this.restAPI = new InfinispanRestAPI(defaultPort, username, password);
   }

   @Override
   public boolean isCoordinator() {
      return coordinator;
   }

   @Override
   protected InfinispanCacheManagerInfo getInfinispanServerClustered() {
      InfinispanCacheManagerInfo infinispanCacheManagerInfo;
      // if the server is busy, the client will throw an exception and we will return an empty object
      // let's the cluster business logic deal with that
      try {
         CacheManagerInfo cacheManagerInfo = restAPI.getCacheManager();
         infinispanCacheManagerInfo = createBasedOn(cacheManagerInfo);
         this.coordinator = cacheManagerInfo.isCoordinator();
      } catch (InfinispanRestAPI.RestException e) {
         log.error(e.getMessage(), e);
         infinispanCacheManagerInfo = new InfinispanCacheManagerInfo();
      }
      return infinispanCacheManagerInfo;
   }

   private InfinispanCacheManagerInfo createBasedOn(CacheManagerInfo cacheManagerInfo) {
      String membersString = cacheManagerInfo.getClusterMembers().toString();
      String nodeAddress = cacheManagerInfo.getNodeAddress();
      return new InfinispanCacheManagerInfo(membersString, nodeAddress);
   }

   public InfinispanRestAPI getRestAPI() {
      return restAPI;
   }
}
