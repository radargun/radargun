package org.radargun.stats.representation;

import org.radargun.config.PropertyHelper;
import org.radargun.stats.OperationStats;
import org.radargun.utils.ReflexiveConverters;

/**
 * Base and converters for representation values defined through configuration.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class RepresentationType {

   public abstract double getValue(OperationStats stats, long duration);

   @Override
   public String toString() {
      return PropertyHelper.getDefinitionElementName(getClass()) + PropertyHelper.toString(this);
   }

   public static class ComplexConverter extends ReflexiveConverters.ObjectConverter {
      public ComplexConverter() {
         super(RepresentationType.class);
      }
   }

   public static class SimpleConverter extends ReflexiveConverters.SimpleConverter {
      protected SimpleConverter() {
         super(RepresentationType.class);
      }
   }
}
