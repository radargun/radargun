package org.radargun.service.redis;


import org.radargun.traits.BasicOperations;
import redis.clients.jedis.JedisCluster;

public class RedisBasicOperations implements BasicOperations {

   private RedisService redisService;

   public RedisBasicOperations(RedisService redisService) {
      this.redisService = redisService;
   }

   @SuppressWarnings("unchecked")
   @Override
   public <K, V> Cache<K, V> getCache(String cacheName) {
      return new RedisCacheAdapter(redisService.getJedisCluster());
   }

   private class RedisCacheAdapter<K, V> implements BasicOperations.Cache<byte[], byte[]> {
      private JedisCluster jedisCluster;

      public RedisCacheAdapter(JedisCluster jedisCluster) {
         this.jedisCluster = jedisCluster;
      }

      @Override
      public byte[] get(byte[] key) {
         return jedisCluster.get(key);
      }

      @Override
      public boolean containsKey(byte[] key) {
         return jedisCluster.exists(key);
      }

      @Override
      public void put(byte[] key, byte[] value) {
         jedisCluster.set(key, value);
      }

      @Override
      public byte[] getAndPut(byte[] key, byte[] value) {
         return jedisCluster.getSet(key, value);
      }


      @Override
      public boolean remove(byte[] key) {
         return jedisCluster.del(key) > 0;
      }

      @Override
      public byte[] getAndRemove(byte[] key) {
         byte[] bytes = jedisCluster.get(key);
         jedisCluster.del(key);
         return bytes;
      }

      /**
       * Radargun's architecture doesn't allow to somehow clean up the JedisPool. Thus, we're using this hack
       * to clean up the pool with ClearStage in order to have fair numbers -->
       */
      @Override
      public void clear() {
         redisService.clearJedisCluster();
      }

   }
}
