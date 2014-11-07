package org.radargun.stages.cache.generators;

import java.util.Random;

import org.radargun.config.Init;
import org.radargun.config.Property;

/**
 * Generates values containing specified cache name. Optionally, it allows to specify suffix
 * for more fine-grained value distinction.
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class CacheAwareTextGenerator implements ValueGenerator {

   @Property(optional = false, doc = "Cache name to be included in the generated value.")
   private String cacheName;

   @Property(doc = "String encoded into the value so that the entry may be distinguished from entries loaded in " +
         "different load stages. Default is empty string.")
   private String suffix = "";

   public static final String VALUE_TEMPLATE = "value_%s_%s@%s";

   public CacheAwareTextGenerator() {
   }

   public CacheAwareTextGenerator(String cacheName, String suffix) {
      this.cacheName = cacheName;
      this.suffix = suffix;
   }

   @Init
   public void init() {
      if (cacheName == null) {
         throw new IllegalArgumentException("Cache name is required to be specified.");
      }
   }

   @Override
   public Object generateValue(Object key, int size, Random random) {
      return String.format(VALUE_TEMPLATE, key, suffix, cacheName);
   }

   @Override
   public int sizeOf(Object value) {
      String s = (String) value;
      return s == null ? -1 : s.length();
   }

   @Override
   public boolean checkValue(Object value, Object key, int expectedSize) {
      return String.format(VALUE_TEMPLATE, key, suffix, cacheName).equals(value);
   }

   public String getCacheName() {
      return cacheName;
   }

   public String getSuffix() {
      return suffix;
   }
}