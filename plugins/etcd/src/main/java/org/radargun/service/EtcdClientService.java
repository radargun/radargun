package org.radargun.service;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import com.coreos.jetcd.Client;
import com.coreos.jetcd.KV;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.EtcdAddressListConverter;

@Service(doc = "Etcd client")
public class EtcdClientService implements Lifecycle {

   private Client client;

   KV kvClient;

   @Property(doc = "List of server addresses (in host:port format) the clients should connect to, separated by semicolons (;).", converter = EtcdAddressListConverter.class)
   protected List<InetSocketAddress> servers;

   @Override
   public void start() {
      List<String> endpoints = new ArrayList<>(servers.size());
      for (InetSocketAddress server : servers) {
         endpoints.add(String.format("http://%s:%s", server.getHostName(), server.getPort()));
      }
      this.client = Client.builder().endpoints(endpoints).build();
      this.kvClient = client.getKVClient();
   }

   @Override
   public void stop() {
      this.kvClient.close();
      this.client.close();
      this.kvClient = null;
      this.client = null;
   }

   @Override
   public boolean isRunning() {
      return kvClient != null;
   }

   @ProvidesTrait
   public EtcdClientOperations createOperations() {
      return new EtcdClientOperations(this);
   }

   @ProvidesTrait
   public EtcdClientService getSelf() {
      return this;
   }

}
