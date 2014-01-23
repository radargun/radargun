package org.radargun.cachewrappers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.infinispan.affinity.KeyAffinityService;
import org.infinispan.affinity.KeyAffinityServiceFactory;
import org.radargun.features.KeyGeneratorAware;
import org.radargun.stressors.KeyGenerator;

/**
 * CacheWrapper implementation that produces a key generator generating keys through a key affinity service.
 * Produced keys are always local to that particular node on which the key generator is running.
 *
 * @author Martin Gencur
 */
public class InfinispanKeyGeneratorAware implements KeyGeneratorAware {

   protected final Log log = LogFactory.getLog(InfinispanKeyGeneratorAware.class);

   private Infinispan51Wrapper wrapper;
   private KeyGenerator keyAffinityStringKeyGenerator;

   public InfinispanKeyGeneratorAware(Infinispan51Wrapper wrapper) {
      this.wrapper = wrapper;
   }

   @Override
   public KeyGenerator getKeyGenerator(int keyBufferSize) {
      if (keyAffinityStringKeyGenerator == null) {
         keyAffinityStringKeyGenerator = new KeyAffinityStringKeyGenerator(keyBufferSize);
      }
      return keyAffinityStringKeyGenerator;
   }

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
    */
   protected class KeyAffinityStringKeyGenerator implements KeyGenerator {

      private KeyAffinityService affinityService;
      private ExecutorService executor;
      private AddressAwareStringKeyGenerator generator;
      private int keyBufferSize;

      public KeyAffinityStringKeyGenerator(int keyBufferSize) {
         this.keyBufferSize = keyBufferSize;
      }

      @Override
      public void init(String param, ClassLoader classLoader) {
      }

      @Override
      public Object generateKey(long keyIndex) {
         synchronized (this) {
            if (affinityService == null) {
               newKeyAffinityService();
            }
         }
         return affinityService.getKeyForAddress(wrapper.getCacheManager().getAddress());
      }

      private void newKeyAffinityService() {
         generator = new AddressAwareStringKeyGenerator(wrapper.getCacheManager().getAddress().toString());
         executor = Executors.newSingleThreadExecutor();
         affinityService = KeyAffinityServiceFactory.newLocalKeyAffinityService(wrapper.getCache(null), generator, executor, keyBufferSize);
         log.info("Created key affinity service with keyBufferSize: " + keyBufferSize);
         Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
               executor.shutdown();
            }
         });
      }
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


