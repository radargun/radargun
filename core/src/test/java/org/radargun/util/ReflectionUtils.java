package org.radargun.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Test support class providing reflection tools.
 *
 * @author Matej Cimbora
 */
public final class ReflectionUtils {

   private ReflectionUtils() {
   }

   public static <T> T getClassProperty(Class clazz, Object invoker, String property, Class<? extends T> castTo)
         throws NoSuchFieldException, IllegalAccessException {
      Field field = getClassField(clazz, property);
      field.setAccessible(true);
      return (T) field.get(invoker);
   }

   public static void setClassProperty(Class clazz, Object invoker, String property, Object value)
         throws NoSuchFieldException, IllegalAccessException {
      Field field = getClassField(clazz, property);
      field.setAccessible(true);
      Field modifiersField = Field.class.getDeclaredField("modifiers");
      modifiersField.setAccessible(true);
      modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
      field.set(invoker, value);
   }

   public static <T> Constructor<T> getConstructor(Class clazz, Class<?>... parameterTypes)
         throws NoSuchMethodException {
      Constructor<T> constructor = clazz.getDeclaredConstructor(parameterTypes);
      constructor.setAccessible(true);
      return constructor;
   }

   private static Field getClassField(Class clazz, String property) throws NoSuchFieldException {
      Field field = null;
      while (field == null) {
         try {
            field = clazz.getDeclaredField(property);
            break;
         } catch (NoSuchFieldException e) {
            clazz = clazz.getSuperclass();
            // No more superclasses
            if (clazz == null) {
               throw e;
            }
         }
      }
      return field;
   }
}
