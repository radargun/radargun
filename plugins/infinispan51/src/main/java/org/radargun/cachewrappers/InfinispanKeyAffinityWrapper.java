package org.radargun.cachewrappers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.infinispan.affinity.KeyAffinityService;
import org.infinispan.affinity.KeyAffinityServiceFactory;
import org.radargun.features.KeyGeneratorAware;
import org.radargun.stressors.KeyGenerator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CacheWrapper implementation that produces a key generator generating keys through a key affinity service.
 * Produced keys are always local to that particular node on which the key generator is running.
 *
 * @author Martin Gencur
 */
public class InfinispanKeyAffinityWrapper extends InfinispanWrapper implements KeyGeneratorAware {

   private KeyGenerator keyAffinityStringKeyGenerator;

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
    *
    * shared keys vs. non-shared keys:
    *
    * - Keys are never shared between threads running on the same node as the key generator is shared between
    * threads running on the same node and the generator produces a different key (with increasing index) every time.
    * - Keys can only be shared between threads running on different nodes but only between nodes that are key owners.
    * - As a result, using sharedKeys parameter has a bit different semantics from other key generators, the concurrency
    * level is lower.
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
      /**
       * Called only if the keys are not shared.
       */
      @Override
      public Object generateKey(int nodeIndex, int threadIndex, long keyIndex) {
         return generateKey(nodeIndex, threadIndex);
      }

      /**
       * Called only if the keys are not shared.
       */
      @Override
      public Object generateKey(int threadIndex, int keyIndex) {
         synchronized(this) {
            if (affinityService == null) {
               newKeyAffinityService(false);
            }
         }
         assertSameGeneratorType(false);
         return affinityService.getKeyForAddress(cacheManager.getAddress());
      }

      /**
       * Called only if keys are shared. Generated keys are not shared between threads on the same node !!
       */
      @Override
      public Object generateKey(int keyIndex) {
         synchronized (this) {
            if (affinityService == null) {
               newKeyAffinityService(true);
            }
         }
         assertSameGeneratorType(true);
         return affinityService.getKeyForAddress(cacheManager.getAddress());
      }

      private void newKeyAffinityService(boolean sharedKeys) {
         if (sharedKeys) {
            generator = new AddressAwareStringKeyGenerator();
         } else {
            generator = new AddressAwareStringKeyGenerator(cacheManager.getAddress().toString());
         }
         executor = Executors.newSingleThreadExecutor();
         affinityService = KeyAffinityServiceFactory.newLocalKeyAffinityService(getCache(null), generator, executor, keyBufferSize);
         log.info("Created key affinity service with keyBufferSize: " + keyBufferSize);
         Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
               executor.shutdown();
            }
         });
      }

      private void assertSameGeneratorType(boolean sharedKeys) {
         if (generator.isSharedKeys() != sharedKeys) {
            throw new RuntimeException("KeyGenerator was created with sharedKeys parameter set to: " + generator.isSharedKeys() + ", requesting different type!");
         }
      }
   }

   protected class AddressAwareStringKeyGenerator implements org.infinispan.affinity.KeyGenerator {

      private boolean sharedKeys = false;

      private String address;

      private long previousKey;

      /**
       * Address-aware constructor - key generator will generate different keys on each node.
       */
      public AddressAwareStringKeyGenerator(String address) {
         this.address = address;
      }

      /**
       * With this constructor the key generator generates the same keys on all nodes in cluster and therefore
       * different stressors from different nodes might access the same keys but only if the key is local for that node
       */
      public AddressAwareStringKeyGenerator() {
         this.address = "";
      }

      @Override
      public Object getKey() {
         return "key_" + address + "_" + previousKey++;
      }

      public boolean isSharedKeys() {
         return sharedKeys;
      }
   }

}


