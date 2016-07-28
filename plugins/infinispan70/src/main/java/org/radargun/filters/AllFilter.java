package org.radargun.filters;

import java.io.Serializable;

import org.infinispan.filter.KeyValueFilter;
import org.infinispan.metadata.Metadata;

/**
 * Accepts anything.
 */
public class AllFilter<K, V> implements KeyValueFilter<K, V>, Serializable {
   public static final AllFilter INSTANCE = new AllFilter();

   @Override
   public boolean accept(K key, V value, Metadata metadata) {
      return true;
   }
}
