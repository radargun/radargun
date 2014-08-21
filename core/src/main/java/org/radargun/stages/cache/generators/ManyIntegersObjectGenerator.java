package org.radargun.stages.cache.generators;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Random;

import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.utils.Utils;

/**
 * Generates the {@link org.radargun.query.ManyIntegersObject} instances
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ManyIntegersObjectGenerator implements ValueGenerator {

   @Property(doc = "Minimum value for all numbers (inclusive). Default is Integer.MIN_VALUE")
   private int min = Integer.MIN_VALUE;

   @Property(doc = "Maximum value for all numbers (inclusive). Default is Integer.MAX_VALUE")
   private int max = Integer.MAX_VALUE;

   @Property(name = "class", doc = "Class instantiated by this generator. Default is 'org.radargun.query.ManyIntegersObject'.")
   private String clazzName = "org.radargun.query.ManyIntegersObject";

   @Property(doc = "Expected number of integers in this object. Default is 10.")
   private int numInts = 10;

   private Class<?> clazz;
   private Constructor<?> ctor;
   private Field[] fields;

   @Init
   public void init() {
      if (max > min) throw new IllegalArgumentException(String.format("min (%d) must be <= max (%d)", min, max));
      try {
         clazz = Thread.currentThread().getContextClassLoader().loadClass(this.clazzName);
         Class<?>[] params = new Class<?>[numInts];
         fields = new Field[numInts];
         for (int i = 0; i < params.length; ++i) {
            params[i] = int.class;
            fields[i] = clazz.getDeclaredField("int" + i);
            fields[i].setAccessible(true);
         }
         ctor = clazz.getConstructor(params);
      } catch (Exception e) {
         throw new IllegalArgumentException(e);
      }
   }

   @Override
   public Object generateValue(Object key, int size, Random random) {
      Object[] params = new Object[numInts];
      for (int i = 0; i < params.length; ++i) params[i] = randomInt(random);
      try {
         return ctor.newInstance(params);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private int randomInt(Random random) {
      long l = random.nextLong();
      return  max > min ? (int)((l < 0 ? ~l : l) % (max - min + 1) + min) : 0;
   }

   @Override
   public int sizeOf(Object value) {
      return -1;
   }

   @Override
   public boolean checkValue(Object value, int expectedSize) {
      if (!clazz.isInstance(value)) return false;
      try {
         for (Field f : fields) {
            int v = f.getInt(value);
            if (v < min || v > max) return false;
         }
         return true;
      } catch (IllegalAccessException e) {
         throw new IllegalStateException(e);
      }
   }
}
