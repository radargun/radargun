package org.radargun.stages.cache.generators;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.Random;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JpaValueGenerator implements ValueGenerator {

   private Class<?> clazz;
   private Class<? extends Annotation> entityClazz;
   private Constructor<?> ctor;

   @Override
   public void init(String param, ClassLoader classLoader) {
      try {
         entityClazz = (Class<? extends Annotation>) classLoader.loadClass("javax.persistence.Entity");
         clazz = classLoader.loadClass(param);
         if (!clazz.isAnnotationPresent(entityClazz)) {
            throw new IllegalArgumentException("Class " + clazz.getName() + " is not an entity - no @Entity present");
         }
         ctor = clazz.getConstructor(Object.class, int.class, Random.class);
      } catch (Exception e) {
         throw new IllegalArgumentException(param, e);
      }
   }

   @Override
   public Object generateValue(Object key, int size, Random random) {
      try {
         return ctor.newInstance(key, size, random);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public int sizeOf(Object value) {
      if (value instanceof JpaValue) {
         return ((JpaValue) value).size();
      } else {
         throw new IllegalArgumentException();
      }
   }

   @Override
   public boolean checkValue(Object value, int expectedSize) {
      return clazz.isInstance(value) && ((JpaValue) value).check(expectedSize);
   }

   public static String getRandomString(int size, Random random) {
      StringBuilder sb = new StringBuilder(size);
      for (int i = 0; i < size; ++i) {
         sb.append((char) (random.nextInt(26) + 'A'));
      }
      return sb.toString();
   }

   public static abstract class JpaValue implements Serializable {
      public int size() {
         throw new UnsupportedOperationException();
      }

      public boolean check(int expectedSize) {
         return true;
      }
   }

}
