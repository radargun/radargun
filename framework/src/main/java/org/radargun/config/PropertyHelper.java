package org.radargun.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Helper for retrieving properties from the class
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 12/12/12
 */
public class PropertyHelper {

   private static Log log = LogFactory.getLog(PropertyHelper.class);

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

   public static void copyProperties(Object source, Object destination) {
      Map<String, Field> sourceProperties = getProperties(source.getClass());
      Map<String, Field> destProperties = getProperties(destination.getClass());
      for (Map.Entry<String, Field> property : sourceProperties.entrySet()) {
         Field destField = destProperties.get(property.getKey());
         if (destField == null) {
            log.trace("Property " + property.getKey() + " not found on destination, skipping");
            continue;
         }
         property.getValue().setAccessible(true);
         destField.setAccessible(true);
         try {
            destField.set(destination, property.getValue().get(source));
         } catch (IllegalAccessException e) {
            log.error(String.format("Failed to copy %s.%s (%s) to %s.%s (%s)",
               source.getClass().getSimpleName(), property.getValue().getName(), source,
               destination.getClass().getSimpleName(), destField.getName(), destination), e);
         }
      }
   }
}
