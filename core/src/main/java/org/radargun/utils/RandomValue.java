package org.radargun.utils;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;

import java.util.Random;

/**
 * Random configuration values.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface RandomValue<T> {
   T nextValue(Random random);

   class PrimitiveConverter extends ReflexiveConverters.ObjectConverter {
      public <T> PrimitiveConverter() {
         super(new Class[] { StringValue.class, IntegerValue.class, DoubleValue.class });
      }
   }

   class NumberConverter extends ReflexiveConverters.ObjectConverter {
      public NumberConverter() {
         super(new Class[] { IntegerValue.class, DoubleValue.class });
      }
   }

   class StringConverter extends ReflexiveConverters.ObjectConverter {
      public StringConverter() {
         super(new Class[] { StringValue.class });
      }
   }

   @DefinitionElement(name = "string", doc = "Generates string objects")
   class StringValue implements RandomValue<java.lang.String> {
      @Property(doc = "Minimum length.", optional = false)
      int minLenght;

      @Property(doc = "Maximum length.", optional = false)
      int maxLenght;

      @Property(doc = "Prefix string. By default empty.")
      String prefix;

      @Property(doc = "Suffix string. By default empty.")
      String suffix;

      @Override
      public String nextValue(Random random) {
         // TODO: set alphabet
         String str = RandomHelper.randomString(minLenght, maxLenght, random);
         if (prefix != null) str = prefix + str;
         if (suffix != null) str = str + suffix;
         return str;
      }
   }

   @DefinitionElement(name = "int", doc = "Generates integer numbers")
   class IntegerValue implements RandomValue<java.lang.Integer> {
      @Property(doc = "Minimum value, inclusive. Default is -2^31.")
      long min = Integer.MIN_VALUE;

      @Property(doc = "Maximum value, inclusive. Default is 2^31 - 1.")
      long max = Integer.MAX_VALUE;

      @Override
      public Integer nextValue(Random random) {
         long l = random.nextLong();
         return max >= min ? (int)((l < 0 ? ~l : l) % (max - min + 1) + min) : 0;
      }
   }

   @DefinitionElement(name = "double", doc = "Generates floating-point numbers")
   class DoubleValue implements RandomValue<java.lang.Double> {
      @Property(doc = "Minimum value, inclusive. Default is 0.")
      double min = 0;

      @Property(doc = "Maximum value, exclusive. Default is 1.")
      double max = 1;

      @Override
      public Double nextValue(Random random) {
         long l = random.nextLong();
         return max > min ? random.nextDouble() * (max - min) + min : 0d;
      }
   }
}
