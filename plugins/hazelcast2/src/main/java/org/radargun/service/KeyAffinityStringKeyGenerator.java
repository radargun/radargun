package org.radargun.service;

import org.radargun.stages.cache.generators.StringKeyGenerator;

/**
 * Class representing custom key generator. Allows to use hazelcast in performance tests alongside with other plugins
 * using 'KeyAffinityStringKeyGenerator'.
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class KeyAffinityStringKeyGenerator extends StringKeyGenerator {

   @Override
   public void init(String param, ClassLoader classLoader) {
      // ignore params
   }
}
