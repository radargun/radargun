package org.radargun.service;

/**
 * Hazelcast client-server operations are the same as "library mode" operations
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */

public class Hazelcast37ClientOperations extends Hazelcast36Operations {

   protected final Hazelcast37ClientService service;

   public Hazelcast37ClientOperations(Hazelcast37ClientService service) {
      this.service = service;
   }

   @Override
   public <K, V> HazelcastCache<K, V> getCache(String cacheName) {
      return new HazelcastOperations.Cache<>(service.<K, V>getMap(cacheName));
   }
}
