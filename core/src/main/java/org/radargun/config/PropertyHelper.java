package org.radargun.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.radargun.Master;
import org.radargun.Slave;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Helper for retrieving properties from the class
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class PropertyHelper {

   private PropertyHelper() {
   }

   private static Log log = LogFactory.getLog(PropertyHelper.class);

   /**
    * Retrieve all properties from this class and all its superclasses.
    *
    * @param clazz
    * @param useDashedName Convert property names to dashed form - e.g. myPropertyName becomes my-property-name.
    * @param includeDelegates Include also those tagged with {@link PropertyDelegate}.
    * @param includeAliases Include alternative names of the properties.
    * @return Map of names of the properties (either dashed or camel cased) to {@link Path paths} in the object graph starting from the given class.
    */
   public static Map<String, Path> getProperties(Class<?> clazz, boolean useDashedName, boolean includeDelegates, boolean includeAliases) {
      Map<String, Path> properties = new TreeMap<String, Path>();
      addProperties(clazz, properties, useDashedName, includeDelegates, includeAliases, "", null);
      return properties;
   }

   /**
    * Retrieve all properties from this class (not including its superclasses).
    *
    * @param clazz
    * @return Map of names of the properties (either dashed or camel cased) to {@link Path paths}
    * in the object graph starting from the given class.
    */
   public static Map<String, Path> getDeclaredProperties(Class<?> clazz, boolean includeDelegates, boolean includeAliases) {
      Map<String, Path> properties = new TreeMap<String, Path>();
      addDeclaredProperties(clazz, properties, false, includeDelegates, includeAliases, "", null);
      return properties;
   }

   /**
    * Retrieve string representation of property's value on the source object.
    * @param path
    * @param source Object where the paths start.
    * @return String representation of the value (as retrieved from its converter).
    */
   public static String getPropertyString(Path path, Object source) {
      Object value = null;
      try {
         value = path.get(source);
         Constructor<? extends Converter<?>> ctor = path.getTargetAnnotation().converter().getDeclaredConstructor();
         ctor.setAccessible(true);
         Converter converter = ctor.newInstance();
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

   private static void addProperties(Class<?> clazz, Map<String, Path> properties, boolean useDashedName, boolean includeDelegates, boolean includeAliases, String prefix, Path path) {
      if (clazz == null) return;
      addDeclaredProperties(clazz, properties, useDashedName, includeDelegates, includeAliases, prefix, path);
      addProperties(clazz.getSuperclass(), properties, useDashedName, includeDelegates, includeAliases, prefix, path);
   }

   private static void addDeclaredProperties(Class<?> clazz, Map<String, Path> properties, boolean useDashedName, boolean includeDelegates, boolean includeAliases, String prefix, Path path) {
      for (Field field : clazz.getDeclaredFields()) {
         if (Modifier.isStatic(field.getModifiers())) continue; // property cannot be static
         Property property = field.getAnnotation(Property.class);
         PropertyDelegate delegate = field.getAnnotation(PropertyDelegate.class);
         if (property != null && delegate != null) {
            // TODO: this is not necessary, but setting more fields from one property would be complicated
            throw new IllegalArgumentException(String.format("Field %s.%s cannot be declared with both @Property and @PropertyDelegate", clazz.getName(), field.getName()));
         }
         Path newPath = path == null ? new Path(field) : path.with(field);
         if (property != null) {
            String name = prefix + getPropertyName(field, property);
            if (useDashedName) {
               name = XmlHelper.camelCaseToDash(name);
            }
            newPath.setComplete(true);
            properties.put(name, newPath);
            if (includeAliases) {
               String deprecatedName = property.deprecatedName();
               if (!deprecatedName.equals(Property.NO_DEPRECATED_NAME)) {
                  if (useDashedName) {
                     deprecatedName = XmlHelper.camelCaseToDash(deprecatedName);
                  }
                  properties.put(deprecatedName, newPath);
               }
            }
         }
         if (delegate != null) {
            String delegatePrefix = useDashedName ? XmlHelper.camelCaseToDash(delegate.prefix()) : delegate.prefix();
            if (includeDelegates) {
               int i = delegatePrefix.length();
               for (; i > 0; --i) {
                  if (Character.isLetterOrDigit(delegatePrefix.charAt(i - 1))) break;
               }
               properties.put(delegatePrefix.substring(0, i), newPath);
            }
            // TODO: delegate properties are added according to field type, this does not allow polymorphism
            addProperties(field.getType(), properties, useDashedName,
                  includeDelegates, includeAliases, prefix + delegatePrefix, newPath);
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
      Map<String, Path> sourceProperties = getProperties(source.getClass(), false, false, false);
      Map<String, Path> destProperties = getProperties(destination.getClass(), false, false, true);
      for (Map.Entry<String, Path> property : sourceProperties.entrySet()) {
         Path destPath = destProperties.get(property.getKey());
         if (destPath == null) {
            log.trace("Property " + property.getKey() + " not found on destination, skipping");
            continue;
         }
         try {
            destPath.set(destination, property.getValue().get(source));
         } catch (IllegalAccessException e) {
            log.errorf(e, "Failed to copy %s (%s) to %s.%s (%s)",
               property.getValue(), source, destPath, destination);
         }
      }
   }

   /**
    * Set properties on the target object using values from the propertyMap.
    * The keys in propertyMap use property name, values are evaluated and converted here.
    *
    * @param target The modified object.
    * @param propertyMap Source of the data, not evaluated
    * @param ignoreMissingProperty If the property is not found on the target object, should we throw and exception?
    * @param useDashedName Expect that the property names in propertyMap use the dashed form.
    */
   public static void setProperties(Object target, Map<String, String> propertyMap, boolean ignoreMissingProperty, boolean useDashedName) {
      Class targetClass = target.getClass();
      Map<String, Path> properties = getProperties(target.getClass(), useDashedName, false, true);

      for (Map.Entry<String, String> entry : propertyMap.entrySet()) {
         String propName = entry.getKey();

         Path path = properties.get(propName);
         if (path != null) {
            Property propertyAnnotation = path.getTargetAnnotation();
            if (propertyAnnotation.readonly()) {
               throw new IllegalArgumentException("Property " + propName + " -> " + path + " is readonly and therefore cannot be set!");
            }
            setPropertyFromString(target, propName, path, entry.getValue());
            continue;
         }
         if (ignoreMissingProperty) {
            log.trace("Property " + propName + " could not be set on class [" + targetClass + "]");
         } else {
            throw new IllegalArgumentException("Couldn't find a property for parameter " + propName + " on class [" + targetClass + "]");
         }
      }
   }

   /**
    * Set properties on the target object using values from the propertyMap.
    *
    * @param target The modified object.
    * @param propertyMap Map of property names to the (possibly complex) definitions.
    * @param ignoreMissingProperty If the property is not found on the target object, should we throw and exception?
    * @param useDashedName Expect that the property names in propertyMap use the dashed form.
    */
   public static void setPropertiesFromDefinitions(Object target, Map<String, Definition> propertyMap, boolean ignoreMissingProperty, boolean useDashedName) {
      Class targetClass = target.getClass();
      Map<String, Path> properties = getProperties(target.getClass(), useDashedName, true, true);

      for (Map.Entry<String, Definition> entry : propertyMap.entrySet()) {
         String propName = entry.getKey();

         Path path = properties.get(propName);
         if (path != null) {
            if (!path.isComplete()) {
               try {
                  if (entry.getValue() instanceof SimpleDefinition) {
                     setProperties(path.get(target), Collections.singletonMap("", ((SimpleDefinition) entry.getValue()).value), ignoreMissingProperty, useDashedName);
                  } else if (entry.getValue() instanceof ComplexDefinition) {
                     setPropertiesFromDefinitions(path.get(target), ((ComplexDefinition) entry.getValue()).getAttributeMap(), ignoreMissingProperty, useDashedName);
                  } else throw new IllegalArgumentException("Unknown definition type: " + entry.getValue());
               } catch (IllegalAccessException e) {
                  throw new IllegalArgumentException("Failed to set " + propName + " on " + target, e);
               }
               continue;
            }
            Property propertyAnnotation = path.getTargetAnnotation();
            if (propertyAnnotation.readonly()) {
               throw new IllegalArgumentException("Property " + propName + " -> " + path + " is readonly and therefore cannot be set!");
            }
            if (entry.getValue() instanceof SimpleDefinition) {
               setPropertyFromString(target, propName, path, ((SimpleDefinition) entry.getValue()).value);
            } else if (entry.getValue() instanceof ComplexDefinition) {
               Class<? extends ComplexConverter<?>> converterClass = propertyAnnotation.complexConverter();
               try {
                  Constructor<? extends ComplexConverter> ctor = converterClass.getDeclaredConstructor();
                  ctor.setAccessible(true);
                  ComplexConverter converter = ctor.newInstance();
                  path.set(target, converter.convert((ComplexDefinition) entry.getValue(), path.getTargetGenericType()));
               } catch (InstantiationException e) {
                  log.errorf(e, "Cannot instantiate converter %s for setting %s (%s)",
                        converterClass.getName(), path, propName);
                  throw new IllegalArgumentException(e);
               } catch (IllegalAccessException e) {
                  log.errorf(e, "Cannot access converter %s for setting %s (%s)",
                        converterClass.getName(), path, propName);
                  throw new IllegalArgumentException(e);
               } catch (Throwable t) {
                  log.error("Failed to convert definition " + entry.getValue(), t);
                  throw new IllegalArgumentException(t);
               }
            } else {
               throw new IllegalArgumentException("Unknown definition type: " + entry.getValue());
            }
            continue;
         }
         if (ignoreMissingProperty) {
            log.trace("Property " + propName + " could not be set on class [" + targetClass + "]");
         } else {
            throw new IllegalArgumentException("Couldn't find a property for parameter " + propName + " on class [" + targetClass + "]");
         }
      }
   }

   private static void setPropertyFromString(Object target, String propName, Path path, String propertyString) {
      Class<? extends Converter> converterClass = path.getTargetAnnotation().converter();
      try {
         Constructor<? extends Converter> ctor = converterClass.getDeclaredConstructor();
         ctor.setAccessible(true);
         Converter converter = ctor.newInstance();
         path.set(target, converter.convert(Evaluator.parseString(propertyString), path.getTargetGenericType()));
      } catch (InstantiationException e) {
         log.errorf(e, "Cannot instantiate converter %s for setting %s (%s)",
               converterClass.getName(), path, propName);
         throw new IllegalArgumentException(e);
      } catch (IllegalAccessException e) {
         log.errorf(e, "Cannot access converter %s for setting %s (%s)",
               converterClass.getName(), path, propName);
         throw new IllegalArgumentException(e);
      } catch (Throwable t) {
         log.error("Failed to convert value " + propertyString, t);
         throw new IllegalArgumentException(t);
      }
   }

   /**
    * Write all properties of this object to string.
    *
    * @param target
    * @return String in form ' {property1=value1, property2=value2, ... }'
    */
   public static String toString(Object target) {
      StringBuilder sb = new StringBuilder(" {");
      Set<Map.Entry<String, Path>> properties = PropertyHelper.getProperties(target.getClass(), false, false, false).entrySet();

      for (Iterator<Map.Entry<String, Path>> iterator = properties.iterator(); iterator.hasNext(); ) {
         Map.Entry<String, Path> property = iterator.next();
         String propertyName = property.getKey();
         Path path = property.getValue();
         sb.append(propertyName).append('=');
         sb.append(PropertyHelper.getPropertyString(path, target));
         if (iterator.hasNext()) {
            sb.append(", ");
         }
      }
      return sb.append(" }").toString();
   }

   /**
    * @param clazz
    * @return Name used in the {@link org.radargun.config.DefinitionElement} annotation, or simple name.
    */
   public static String getDefinitionElementName(Class<?> clazz) {
      DefinitionElement de = clazz.getAnnotation(DefinitionElement.class);
      return de != null ? de.name() : clazz.getSimpleName();
   }
}
