package org.radargun.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.infinispan.affinity.KeyAffinityService;
import org.infinispan.affinity.KeyAffinityServiceFactory;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.cache.generators.KeyGenerator;

/**
 * A key generator using a key affinity service. All keys produced by this key generator
 * are local to the node that requested them.
 * The generator does not honour the keyIndex passed as argument and always returns a new unique key.
 *
 * Using shared keys with this generator is possible, but the set of generated keys is different on each node
 * (this is very likely even if the key format was identical, we ensure that by adding the local node address).
 * Initially only the local entries will be loaded into cache. Then, as the node executes PUT requests, the cache
 * will be filled with entries that are considered non-local, but have different keys. This will result in cache
 * with numEntries * numNodes.
 * Previous GET operation will return null values, naturally.
 *
 * Therefore, using shared keys with this generator is not advisable.
 *
 * Note for RadarGun integration: as key generators can be loaded only from core libraries, you have to use
 * &lt;plugin-specific class="org.radargun.service.KeyAffinityStringKeyGenerator"
 *                     params="keyBufferSize:1000;cache=testCache" /&gt;
 * to use this generator in test.
 *
 */
public class KeyAffinityStringKeyGenerator implements KeyGenerator {

   protected final Log log = LogFactory.getLog(KeyAffinityStringKeyGenerator.class);

   @Property(doc = "Number of generated keys per node.", optional = false)
   private int keyBufferSize;

   @Property(doc = "Name of the cache where the keys will be stored.", optional = false)
   private String cache;

   private KeyAffinityService affinityService;
   private ExecutorService executor;
   private AddressAwareStringKeyGenerator generator;
   private Infinispan51EmbeddedService wrapper;

   @Init
   public void init() {
      if (keyBufferSize <= 0 || cache == null) {
         throw new IllegalArgumentException("Invalid parameters provided, 'keyBufferSize' and 'cache' need to be specified.");
      }
      wrapper = Infinispan51EmbeddedService.getInstance();
   }

   @Override
   public Object generateKey(long keyIndex) {
      synchronized (this) {
         if (affinityService == null) {
            newKeyAffinityService();
         }
      }
      return affinityService.getKeyForAddress(wrapper.cacheManager.getAddress());
   }

   private void newKeyAffinityService() {
      generator = new AddressAwareStringKeyGenerator(wrapper.cacheManager.getAddress().toString());
      executor = Executors.newSingleThreadExecutor();
      affinityService = KeyAffinityServiceFactory.newLocalKeyAffinityService(wrapper.cacheManager.getCache(cache), generator, executor, keyBufferSize);
      log.info("Created key affinity service with keyBufferSize: " + keyBufferSize);
      Runtime.getRuntime().addShutdownHook(new Thread() {
         public void run() {
            executor.shutdown();
         }
      });
   }

   protected class AddressAwareStringKeyGenerator implements org.infinispan.affinity.KeyGenerator {

      private String address;

      private long previousKey;

      /**
       * Address-aware constructor - key generator will generate different keys on each node.
       */
      public AddressAwareStringKeyGenerator(String address) {
         this.address = address;
      }

      @Override
      public Object getKey() {
         return "key_" + address + "_" + previousKey++;
      }
   }
}
