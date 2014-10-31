package org.radargun.stages.cache.generators;

import org.radargun.utils.ReflexiveConverters;

/**
 * Used for generating keys for caches. All implementations must have an default/no-arg public
 * constructor.
 * <p/>
 * Concurrency: methods of this class might be called from multiple threads concurrently.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public interface KeyGenerator {
   String KEY_GENERATOR = "KEY_GENERATOR";

   /**
    * @param keyIndex
    * @return
    */
   Object generateKey(long keyIndex);

   public static class ComplexConverter extends ReflexiveConverters.ObjectConverter {
      public ComplexConverter() {
         super(KeyGenerator.class);
      }
   }
}
