package org.radargun.stages.iteration;

import java.io.Serializable;

import org.radargun.traits.Iterable;

/**
 * Converts anything to null
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class NullConverter implements Iterable.Converter, Serializable {
   @Override
   public Object convert(Object key, Object value) {
      return null;
   }
}
