package org.radargun.stages.cache.generators;

import java.io.Serializable;
import java.lang.reflect.Constructor;

import org.radargun.config.Init;
import org.radargun.config.Property;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JpaKeyGenerator implements KeyGenerator {
   @Property(name = "class", doc = "Fully qualified name of the key class.", optional = false)
   private String clazzName;

   private Class<?> clazz;
   private Constructor<?> ctor;

   @Init
   public void init() {
      try {
         clazz = Thread.currentThread().getContextClassLoader().loadClass(clazzName);
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
