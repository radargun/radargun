package org.radargun.service.redis;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

@Service(doc = "Redis clustered service")
public class RedisService implements Lifecycle {

   protected final Log log = LogFactory.getLog(getClass());

   @Property(name = "servers", doc = "Address of servers of Redis cluster")
   public String servers;

   @Property(name = "max-pool-size", doc = "Maximum size of JedisPool. Must be adequately set according to number of stressor thread. Default: 8.")
   public int maxPoolSize = 8;

   private boolean running;
   private JedisCluster jedisCluster;

   @ProvidesTrait
   public RedisBasicOperations getBasicOperations() {
      return new RedisBasicOperations(this);
   }

   @ProvidesTrait
   public Lifecycle getLifecycle() {
      return this;
   }

   @Override
   public void start() {
      createJedisCluster();
      running = true;
   }

   private Set<HostAndPort> loadAddresses() {
      Set<HostAndPort> result = new HashSet<>();
      String[] stringAddresses = servers.split(",");
      for (String address : stringAddresses) {
         String[] tokens = address.split(":");

         if (tokens.length != 2) {
            throw new RuntimeException("Address " + address +  " does not match format host:port");
         }

         String host = tokens[0].trim();
         Integer port;
         try {
            port = Integer.parseInt(tokens[1].trim());
         } catch (NumberFormatException nfe) {
            throw new RuntimeException("Port in address " + address +  " is not numeric");
         }

         result.add(new HostAndPort(host, port));
      }

      return result;
   }

   public void clearJedisCluster() {
      try {
         jedisCluster.close();
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
      createJedisCluster();
   }

   @Override
   public void stop() {
      clearJedisCluster();
      running = false;
   }

   @Override
   public boolean isRunning() {
      return running;
   }

   public JedisCluster getJedisCluster() {
      return jedisCluster;
   }

   private void createJedisCluster() {
      GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
      poolConfig.setMaxTotal(maxPoolSize);
      Set<HostAndPort> clusterHostAndPorts = loadAddresses();
      jedisCluster = new JedisCluster(clusterHostAndPorts, poolConfig);
   }

}
