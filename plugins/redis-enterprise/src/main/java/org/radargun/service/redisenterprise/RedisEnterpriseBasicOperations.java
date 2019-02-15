package org.radargun.service.redisenterprise;


import org.radargun.traits.BasicOperations;
import redis.clients.jedis.Jedis;

public class RedisEnterpriseBasicOperations implements BasicOperations {

   private RedisEnterpriseService redisService;

   public RedisEnterpriseBasicOperations(RedisEnterpriseService redisService) {
      this.redisService = redisService;
   }

   @SuppressWarnings("unchecked")
   @Override
   public <K, V> Cache<K, V> getCache(String cacheName) {
      return new RedisCacheAdapter(redisService.getJedis());
   }

   private class RedisCacheAdapter<K, V> implements BasicOperations.Cache<byte[], byte[]> {
      private Jedis jedis;

      public RedisCacheAdapter(Jedis jedis) {
         this.jedis = jedis;
      }

      @Override
      public byte[] get(byte[] key) {
         return jedis.get(key);
      }

      @Override
      public boolean containsKey(byte[] key) {
         return jedis.exists(key);
      }

      @Override
      public void put(byte[] key, byte[] value) {
         jedis.set(key, value);
      }

      @Override
      public byte[] getAndPut(byte[] key, byte[] value) {
         return jedis.getSet(key, value);
      }

      @Override
      public boolean remove(byte[] key) {
         return jedis.del(key) > 0;
      }

      @Override
      public byte[] getAndRemove(byte[] key) {
         byte[] bytes = jedis.get(key);
         jedis.del(key);
         return bytes;
      }

      /**
       * Radargun's architecture doesn't allow to somehow clean up the JedisPool. Thus, we're using this hack
       * to clean up the pool with ClearStage in order to have fair numbers -->
       */
      @Override
      public void clear() {
         redisService.clearJedisPool();
      }

   }
}
