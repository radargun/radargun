package org.radargun.service;

import java.util.Collections;
import java.util.List;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.radargun.Service;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.AddressStringListConverter;

/**
 * Hazelcast client service
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */

@Service(doc = "Hazelcast client")
public class Hazelcast37ClientService implements Lifecycle {

   protected HazelcastInstance hazelcastInstance;

   @Property(doc = "List of server addresses the clients should connect to, separated by semicolons (;).", converter = AddressStringListConverter.class)
   protected String[] servers;

   @Property(doc = "Group name, the default is dev")
   protected String groupName = "dev";

   @Property(doc = "Group password, the default is dev-pass")
   protected String groupPass = "dev-pass";

   @Property(name = "cache", doc = "Name of the map ~ cache", deprecatedName = "map")
   protected String mapName = "default";

   @Property(doc = "Indices that should be build.", complexConverter = Hazelcast36Service.IndexConverter.class)
   protected List<Hazelcast37ClientService.Index> indices = Collections.EMPTY_LIST;

   @Override
   public void start() {
      ClientConfig clientConfig = new ClientConfig();
      clientConfig.getGroupConfig().setName(groupName).setPassword(groupPass);
      clientConfig.getNetworkConfig().addAddress(servers);

      hazelcastInstance = HazelcastClient.newHazelcastClient(clientConfig);
   }

   @Override
   public void stop() {
      hazelcastInstance.getLifecycleService().shutdown();
      hazelcastInstance = null;
   }

   @Override
   public boolean isRunning() {
      return hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning();
   }

   @ProvidesTrait
   public Hazelcast37ClientOperations createOperations() {
      return new Hazelcast37ClientOperations(this);
   }

   @ProvidesTrait
   public Hazelcast37ClientService getSelf() {
      return this;
   }

   @DefinitionElement(name = "index", doc = "Index definition.")
   protected static class Index {
      @Property(doc = "Map on which the index should be built. Default is the default map.")
      protected String mapName;

      @Property(doc = "Should be the index ordered? Default is true")
      protected boolean ordered = true;

      @Property(doc = "Path in the indexed object.", optional = false)
      protected String path;

      // whether we have registered this index
      private boolean added = false;
   }

   protected <K, V> IMap<K, V> getMap(String mapName) {
      if (mapName == null) {
         mapName = this.mapName;
      }
      IMap<K, V> map = hazelcastInstance.getMap(mapName);

      for (Hazelcast37ClientService.Index index : indices) {
         synchronized (index) {
            if (index.added) {
               continue;
            }
            if ((index.mapName == null && map.getName().equals(this.mapName))
                    || (index.mapName != null && map.getName().equals(index.mapName))) {
               map.addIndex(index.path, index.ordered);
               index.added = true;
            }
         }
      }
      return map;
   }
}
