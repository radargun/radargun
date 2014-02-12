package org.radargun.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

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

   public static String getPropertyString(Field propertyField, Object source) {
      propertyField.setAccessible(true);
      Object value = null;
      try {
         value = propertyField.get(source);
         Converter converter = propertyField.getAnnotation(Property.class).converter().newInstance();
         return converter.convertToString(value);
      } catch (IllegalAccessException e) {
         return "<not accessible>";
      } catch (InstantiationException e) {
         return "<cannot create converter: " + value + ">";
      } catch (ClassCastException e) {
         return "<cannot convert: " + value + ">";
      } catch (Throwable t) {
         return "<error " + t + ": " + value + ">";
      }
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

   public static void setProperties(Object target, Map<String, String> propertyMap, boolean ignoreMissingProperty) {
      Class targetClass = target.getClass();
      Map<String, Field> properties = getProperties(target.getClass());

      for (Map.Entry<String, String> entry : propertyMap.entrySet()) {
         String propName = entry.getKey();

         Field field = properties.get(propName);
         if (field != null) {
            Property property = field.getAnnotation(Property.class);
            if (property.readonly()) {
               throw new IllegalArgumentException("Property " + propName + " on class [" + targetClass + "] is readonly and therefore cannot be set!");
            }
            Class<? extends Converter> converterClass = property.converter();
            try {
               Converter converter = converterClass.newInstance();
               field.setAccessible(true);
               field.set(target, converter.convert(entry.getValue(), field.getGenericType()));
               continue;
            } catch (InstantiationException e) {
               log.error(String.format("Cannot instantiate converter %s for setting %s.%s (%s): %s",
                     converterClass.getName(), target.getClass().getName(), field.getName(), propName, e));
            } catch (IllegalAccessException e) {
               log.error(String.format("Cannot access converter %s for setting %s.%s (%s): %s",
                     converterClass.getName(), target.getClass().getName(), field.getName(), propName, e));
            } catch (Throwable t) {
               log.error("Failed to convert value " + entry.getValue() + ": " + t);
            }
         }
         if (ignoreMissingProperty) {
            log.trace("Property " + propName + " could not be set on class [" + targetClass + "]");
         } else {
            throw new IllegalArgumentException("Couldn't find a property for parameter " + propName + " on class [" + targetClass + "]");
         }
      }
   }
}
