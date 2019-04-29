package org.radargun.service.redisenterprise;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service(doc = "Redis 3 clustered service")
public class RedisEnterpriseService implements Lifecycle {

   protected final Log log = LogFactory.getLog(getClass());

   @Property(name = "server", doc = "Address of clustered Redis Enterprise cluster")
   public String server;

   @Property(name = "max-pool-size", doc = "Maximum size of JedisPool. Must be adequately set according to number of stressor thread. Default: 8.")
   public int maxPoolSize = 8;

   private boolean running;
   private JedisPool jedisPool;

   @ProvidesTrait
   public RedisEnterpriseBasicOperations getBasicOperations() {
      return new RedisEnterpriseBasicOperations(this);
   }

   @ProvidesTrait
   public Lifecycle getLifecycle() {
      return this;
   }

   @Override
   public void start() {
      createJedisPool();
      running = true;
   }

   private HostAndPort loadAddresses() {
      String[] tokens = server.split(":");

      if (tokens.length != 2) {
         throw new RuntimeException("Address " + server + " does not match format host:port");
      }

      String host = tokens[0].trim();
      Integer port;
      try {
         port = Integer.parseInt(tokens[1].trim());
      } catch (NumberFormatException nfe) {
         throw new RuntimeException("Port in address " + server + " is not numeric");
      }

      return new HostAndPort(host, port);
   }

   public void clearJedisPool() {
      jedisPool.close();
      createJedisPool();
   }

   @Override
   public void stop() {
      jedisPool.close();
      running = false;
   }

   @Override
   public boolean isRunning() {
      return running;
   }

   public Jedis getJedis() {
      return jedisPool.getResource();
   }

   private void createJedisPool() {
      GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
      poolConfig.setMaxTotal(maxPoolSize);
      HostAndPort clusterHostAndPort = loadAddresses();
      jedisPool = new JedisPool(poolConfig, clusterHostAndPort.getHost(), clusterHostAndPort.getPort());
   }
}