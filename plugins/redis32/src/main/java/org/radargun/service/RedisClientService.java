package org.radargun.service;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.RedisAddressListConverter;
import org.radargun.utils.Utils;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPoolConfig;

/**
 * This only works with clustered redis.
 */
@Service(doc = "Redis client")
public class RedisClientService implements Lifecycle {

   protected JedisCluster jedisCluster;

   @Property(doc = "List of server addresses (in host:port format) the clients should connect to, separated by semicolons (;).", converter = RedisAddressListConverter.class)
   protected List<InetSocketAddress> servers;

   @Property(doc = "Redis port")
   protected int connectionPoolMaxTotal = 100;

   @Property(doc = "Redis port")
   protected int connectionPoolMaxIdle = 100;

   @Property(doc = "Redis port")
   protected int connectionPoolMinIdle = 10;

   @Override
   public void start() {
      Set<HostAndPort> jedisClusterNodes = new HashSet<>();
      for (InetSocketAddress server : servers) {
         jedisClusterNodes.add(new HostAndPort(server.getHostName(), server.getPort()));
      }
      JedisPoolConfig poolConfig = new JedisPoolConfig();
      poolConfig.setMaxTotal(connectionPoolMaxTotal);
      poolConfig.setMaxIdle(connectionPoolMaxIdle);
      poolConfig.setMinIdle(connectionPoolMinIdle);
      jedisCluster = new JedisCluster(jedisClusterNodes, poolConfig);
   }

   @Override
   public void stop() {
      Utils.close(jedisCluster);
      jedisCluster = null;
   }

   @Override
   public boolean isRunning() {
      return jedisCluster != null;
   }

   @ProvidesTrait
   public RedisClientOperations createOperations() {
      return new RedisClientOperations(this);
   }

   @ProvidesTrait
   public RedisClientService getSelf() {
      return this;
   }

}
