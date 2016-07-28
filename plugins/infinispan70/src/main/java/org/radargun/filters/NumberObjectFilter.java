package org.radargun.filters;

import java.io.Serializable;

import org.radargun.config.Property;
import org.radargun.query.NumberObject;
import org.radargun.traits.Iterable;

public class NumberObjectFilter implements Iterable.Filter<Object, NumberObject>, Serializable {
   @Property(doc = "intValue")
   private int integerValue;

   @Override
   public boolean accept(Object key, NumberObject value) {
      return value.getInt() == integerValue;
   }
}
