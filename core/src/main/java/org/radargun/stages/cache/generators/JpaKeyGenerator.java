package org.radargun.stages.cache.generators;

import java.io.Serializable;
import java.lang.reflect.Constructor;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JpaKeyGenerator implements KeyGenerator {
   private Class<?> clazz;
   private Constructor<?> ctor;

   @Override
   public void init(String param, ClassLoader classLoader) {
      try {
         clazz = classLoader.loadClass(param);
         ctor = clazz.getConstructor(long.class);
      } catch (Exception e) {
         throw new IllegalArgumentException(e);
      }
   }

   @Override
   public Object generateKey(long keyIndex) {
      try {
         return ctor.newInstance(keyIndex);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   public static abstract class JpaKey implements Serializable {
   }
}
