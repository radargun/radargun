package org.radargun.service;

import org.radargun.config.Property;
import org.radargun.stages.cache.generators.StringKeyGenerator;

/**
 * Class representing custom key generator. Allows to use hazelcast in performance tests alongside with other plugins
 * using 'KeyAffinityStringKeyGenerator'.
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class KeyAffinityStringKeyGenerator extends StringKeyGenerator {
   @Property(doc = "Number of generated keys per node.", optional = false)
   private int keyBufferSize;

   @Property(doc = "Name of the cache where the keys will be stored.", optional = false)
   private String cache;
}
