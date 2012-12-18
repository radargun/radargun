package org.radargun.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

/**
 * Helper for retrieving properties from the class
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 12/12/12
 */
public class PropertyHelper {

   public static Map<String, Field> getProperties(Class<?> clazz) {
      Map<String, Field> properties = new TreeMap<String, Field>();
      addProperties(clazz, properties);
      return properties;
   }

   public static Map<String, Field> getDeclaredProperties(Class<?> clazz) {
      Map<String, Field> properties = new TreeMap<String, Field>();
      addDeclaredProperties(clazz, properties);
      return properties;
   }

   public static String getPropertyName(Field property) {
      Property annotation = property.getAnnotation(Property.class);
      return getPropertyName(property, annotation);
   }

   private static String getPropertyName(Field property, Property annotation) {
      return annotation.name().equals(Property.FIELD_NAME) ? property.getName() : annotation.name();
   }

   private static void addProperties(Class<?> clazz, Map<String, Field> properties) {
      if (clazz == null) return;
      addDeclaredProperties(clazz, properties);
      addProperties(clazz.getSuperclass(), properties);
   }

   private static void addDeclaredProperties(Class<?> clazz, Map<String, Field> properties) {
      for (Field field : clazz.getDeclaredFields()) {
         if (Modifier.isStatic(field.getModifiers())) continue; // property cannot be static
         Property property = field.getAnnotation(Property.class);
         if (property != null) {
            properties.put(getPropertyName(field, property), field);
            if (!property.deprecatedName().equals(Property.NO_DEPRECATED_NAME)) {
               properties.put(property.deprecatedName(), field);
            }
         }
      }
   }
}
