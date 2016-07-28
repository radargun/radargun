package org.radargun.stages.iteration;

import java.io.Serializable;

import org.radargun.traits.Iterable;

/**
 * Rejects all entries.
 */
public class NoneFilter implements Iterable.Filter, Serializable {
   @Override
   public boolean accept(Object key, Object value) {
      return false;
   }
}
