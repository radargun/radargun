package org.radargun.stages.iteration;

import java.io.Serializable;

import org.radargun.traits.Iterable;

/**
 * Converts anything to null
 */
public class NullConverter implements Iterable.Converter, Serializable {
   @Override
   public Object convert(Object key, Object value) {
      return null;
   }
}
