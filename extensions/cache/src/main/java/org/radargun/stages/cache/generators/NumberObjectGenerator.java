package org.radargun.stages.cache.generators;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Random;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Generates number objects NumberObject (by default it is org.radargun.query.NumberObject)
 * should have constructor and method with these signatures:
 *
 * {@code}
 * public class NumberObject {
 *    public NumberObject(int i, double d) { ... }
 *    public int getInt() { ... }
 *    public double getDouble() { ... }
 * }
 * {@code}
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "number-object", doc = "Generates objects with integer and double values.")
public class NumberObjectGenerator implements ValueGenerator {
   protected static Log log = LogFactory.getLog(NumberObjectGenerator.class);

   @Property(doc = "Minimal value (inclusive) of generated integer part.")
   private long intMin = Integer.MIN_VALUE;

   @Property(doc = "Maximal value (inclusive) of generated integer part.")
   private long intMax = Integer.MAX_VALUE;

   @Property(doc = "Minimal value (inclusive) of generated double part.")
   private double doubleMin = 0;

   @Property(doc = "Maximal value (exclusive) of generated double part.")
   private double doubleMax = 1;

   @Property(name = "class", doc = "Class instantiated by this generator. Default is 'org.radargun.query.NumberObject'.")
   private String clazz = "org.radargun.query.NumberObject";

   private Constructor<?> ctor;
   private Method getInt;
   private Method getDouble;

   @Init
   public void init() {
      try {
         Class<?> clazz = Class.forName(this.clazz);
         ctor = clazz.getConstructor(int.class, double.class);
         getInt = clazz.getMethod("getInt");
         getDouble = clazz.getMethod("getDouble");
      } catch (Exception e) {
         // trace as this can happen on master node
         log.trace("Could not initialize generator " + this, e);
      }
   }

   @Override
   public Object generateValue(Object key, int size, Random random) {
      long l = random.nextLong();
      int i = intMax >= intMin ? (int) ((l < 0 ? ~l : l) % (intMax - intMin + 1) + intMin) : 0;
      double d = doubleMax > doubleMin ? random.nextDouble() * (doubleMax - doubleMin) + doubleMin : 0d;
      return newInstance(i, d);
   }

   private Object newInstance(int i, double d) {
      if (ctor == null) throw new IllegalStateException("The generator was not properly initialized");
      try {
         return ctor.newInstance(i, d);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public int sizeOf(Object value) {
      return -1;
   }

   @Override
   public boolean checkValue(Object value, Object key, int expectedSize) {
      if (getInt == null || getDouble == null)
         throw new IllegalStateException("The generator was not properly initialized");
      try {
         int integerValue = (Integer) getInt.invoke(value);
         double doubleValue = (Double) getDouble.invoke(value);
         return integerValue >= intMin && integerValue <= intMax
            && doubleValue >= doubleMin && doubleValue < doubleMax;
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public String toString() {
      return PropertyHelper.getDefinitionElementName(getClass()) + PropertyHelper.toString(this);
   }
}
