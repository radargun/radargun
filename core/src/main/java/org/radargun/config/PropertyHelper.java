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

   /**
    * Retrieve all properties from this class and all its superclasses as a map of property name - path pairs.
    * @see Path for details.
    *
    * @param clazz
    * @param useDashedName Convert property names to dashed form - e.g. myPropertyName becomes my-property-name.
    * @return
    */
   public static Map<String, Path> getProperties(Class<?> clazz, boolean useDashedName) {
      Map<String, Path> properties = new TreeMap<String, Path>();
      addProperties(clazz, properties, useDashedName, "", null);
      return properties;
   }

   /**
    *  Retrieve all properties from this class (*not including its superclasses) as a map of property name - path pairs.
    * @see Path for details.
    * @param clazz
    * @return
    */
   public static Map<String, Path> getDeclaredProperties(Class<?> clazz) {
      Map<String, Path> properties = new TreeMap<String, Path>();
      addDeclaredProperties(clazz, properties, false, "", null);
      return properties;
   }

   /**
    * Retrieve string representation of property's value on the source object.
    * @param path
    * @param source
    * @return
    */
   public static String getPropertyString(Path path, Object source) {
      Object value = null;
      try {
         value = path.get(source);
         Converter converter = path.getTargetAnnotation().converter().newInstance();
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

   private static void addProperties(Class<?> clazz, Map<String, Path> properties, boolean useDashedName, String prefix, Path path) {
      if (clazz == null) return;
      addDeclaredProperties(clazz, properties, useDashedName, prefix, path);
      addProperties(clazz.getSuperclass(), properties, useDashedName, prefix, path);
   }

   private static void addDeclaredProperties(Class<?> clazz, Map<String, Path> properties, boolean useDashedName, String prefix, Path path) {
      for (Field field : clazz.getDeclaredFields()) {
         if (Modifier.isStatic(field.getModifiers())) continue; // property cannot be static
         Property property = field.getAnnotation(Property.class);
         PropertyDelegate delegate = field.getAnnotation(PropertyDelegate.class);
         if (property != null && delegate != null) {
            // TODO: this is not necessary, but setting more fields from one property would be complicated
            throw new IllegalArgumentException(String.format("Field %s.%s cannot be declared with both @Property and @PropertyDelegate", clazz.getName(), field.getName()));
         }
         if (property != null) {
            String name = prefix + getPropertyName(field, property);
            if (useDashedName) {
               name = XmlHelper.camelCaseToDash(name);
            }
            Path newPath = path == null ? new Path(field) : path.with(field);
            properties.put(name, newPath);
            String deprecatedName = property.deprecatedName();
            if (!deprecatedName.equals(Property.NO_DEPRECATED_NAME)) {
               if (useDashedName) {
                  deprecatedName = XmlHelper.camelCaseToDash(deprecatedName);
               }
               properties.put(deprecatedName, newPath);
            }
         }
         if (delegate != null) {
            // TODO: delegate properties are added according to field type, this does not allow polymorphism
            addProperties(field.getType(), properties, useDashedName,
                  prefix + delegate.prefix(), path == null ? new Path(field) : path.with(field));
         }
      }
   }

   /**
    * Copy the values of identical properties from source to destination. No evaluation or conversion occurs.
    *
    * @param source
    * @param destination
    */
   public static void copyProperties(Object source, Object destination) {
      Map<String, Path> sourceProperties = getProperties(source.getClass(), false);
      Map<String, Path> destProperties = getProperties(destination.getClass(), false);
      for (Map.Entry<String, Path> property : sourceProperties.entrySet()) {
         Path destPath = destProperties.get(property.getKey());
         if (destPath == null) {
            log.trace("Property " + property.getKey() + " not found on destination, skipping");
            continue;
         }
         try {
            destPath.set(destination, property.getValue().get(source));
         } catch (IllegalAccessException e) {
            log.error(String.format("Failed to copy %s (%s) to %s.%s (%s)",
               property.getValue(), source, destPath, destination), e);
         }
      }
   }

   /**
    * Set properties on the target object using values from the propertyMap.
    * The keys in propertyMap use property name, values are evaluated and converted here.
    *
    * @param target The modified object
    * @param propertyMap Source of the data, not evaluated
    * @param ignoreMissingProperty If the property is not found on the target object, should we throw and exception?
    * @param useDashedName Expect that the property names in propertyMap use the dashed form
    */
   public static void setProperties(Object target, Map<String, String> propertyMap, boolean ignoreMissingProperty, boolean useDashedName) {
      Class targetClass = target.getClass();
      Map<String, Path> properties = getProperties(target.getClass(), useDashedName);

      for (Map.Entry<String, String> entry : propertyMap.entrySet()) {
         String propName = entry.getKey();

         Path path = properties.get(propName);
         if (path != null) {
            if (path.getTargetAnnotation().readonly()) {
               throw new IllegalArgumentException("Property " + propName + " -> " + path + " is readonly and therefore cannot be set!");
            }
            Class<? extends Converter> converterClass = path.getTargetAnnotation().converter();
            try {
               Converter converter = converterClass.newInstance();
               path.set(target, converter.convert(Evaluator.parseString(entry.getValue()), path.getTargetGenericType()));
               continue;
            } catch (InstantiationException e) {
               log.error(String.format("Cannot instantiate converter %s for setting %s (%s): %s",
                     converterClass.getName(), path, propName, e));
            } catch (IllegalAccessException e) {
               log.error(String.format("Cannot access converter %s for setting %s (%s): %s",
                     converterClass.getName(), path, propName, e));
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
