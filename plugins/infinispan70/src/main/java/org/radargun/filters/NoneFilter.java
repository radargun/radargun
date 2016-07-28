package org.radargun.filters;

import java.io.Serializable;

import org.infinispan.filter.KeyValueFilter;
import org.infinispan.metadata.Metadata;

/**
 * Rejects everything.
 * Don't mistake with {@link org.radargun.stages.iteration.NoneFilter} that implements RadarGun filter interface
 * while this one implements Infinispan interface.
 */
public class NoneFilter<K, V> implements KeyValueFilter<K, V>, Serializable {
   public static final NoneFilter INSTANCE = new NoneFilter();

   @Override
   public boolean accept(K key, V value, Metadata metadata) {
      return false;
   }
}