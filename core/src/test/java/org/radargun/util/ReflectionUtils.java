package org.radargun.util;

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

   public static <T> T getClassProperty(Class clazz, Object invoker, String property, Class<? extends T> castTo) throws NoSuchFieldException, IllegalAccessException {
      Field field = clazz.getDeclaredField(property);
      field.setAccessible(true);
      return (T) field.get(invoker);
   }

   public static void setClassProperty(Class clazz, Object invoker, String property, Object value) throws NoSuchFieldException, IllegalAccessException {
      Field field = clazz.getDeclaredField(property);
      field.setAccessible(true);
      Field modifiersField = Field.class.getDeclaredField("modifiers");
      modifiersField.setAccessible(true);
      modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
      field.set(invoker, value);
   }
}
