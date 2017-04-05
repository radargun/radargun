package org.radargun.service;

import org.radargun.traits.BasicOperations;
import redis.clients.jedis.JedisCluster;

public class RedisClientOperations implements BasicOperations {

   protected final RedisClientService service;

   public RedisClientOperations(RedisClientService service) {
      this.service = service;
   }

   @Override
   public <K, V> Cache<K, V> getCache(String cacheName) {
      return new RedisCacheAdapter(service.jedisCluster);
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

      @Override
      public void clear() {
         throw new UnsupportedOperationException("Clearing redis is not supported by jedis.");
      }
   }

}
