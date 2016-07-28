package org.radargun.filters;

import java.io.Serializable;

import org.infinispan.filter.Converter;
import org.infinispan.metadata.Metadata;

/**
 * Converts anything to null.
 * Don't mistake with {@link org.radargun.stages.iteration.NullConverter} that implements RadarGun interface
 * while this one implements Infinispan interface.
 */
public class NullConverter implements Converter, Serializable {
   public static final NullConverter INSTANCE = new NullConverter();

   @Override
   public Object convert(Object key, Object value, Metadata metadata) {
      return null;
   }

}
