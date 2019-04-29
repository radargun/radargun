package org.radargun.service;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.NearCacheConfig;
import org.radargun.Service;
import org.radargun.config.Property;

/**
 * @author Gustavo Lira
 */
@Service(doc = HazelcastService.SERVICE_DESCRIPTION)
public class Hazelcast311ClientService extends Hazelcast37ClientService {

   @Property(doc = "Near caching mode. Default is DISABLED.")
   protected boolean isNearCacheEnabled = false;

   @Property(doc = "Maximum number or entires in near cache. -1 = Max Value")
   protected int maxEntries;

   @Override
   public void start() {
      if(!isNearCacheEnabled) {
         super.start();
         return;
      }

      //Max entries on Hazelcast is not -1 like ISPN
      maxEntries = maxEntries == -1 ? Integer.MAX_VALUE : maxEntries;
      EvictionConfig evictionConfig = new EvictionConfig().setSize(Integer.MAX_VALUE);
      NearCacheConfig nearCacheConfig = new NearCacheConfig().setEvictionConfig(evictionConfig);

      ClientConfig clientConfig = new ClientConfig();
      clientConfig.getGroupConfig().setName(groupName).setPassword(groupPass);
      clientConfig.getNetworkConfig().addAddress(servers);
      clientConfig.addNearCacheConfig(nearCacheConfig);
      ClientNetworkConfig networkConfig = clientConfig.getNetworkConfig();
      networkConfig.setRedoOperation(true);
      clientConfig.setNetworkConfig(networkConfig);

      hazelcastInstance = HazelcastClient.newHazelcastClient(clientConfig);
   }

}