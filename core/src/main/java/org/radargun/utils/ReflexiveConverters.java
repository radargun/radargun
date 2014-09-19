package org.radargun.utils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.Directories;
import org.radargun.config.AnnotatedHelper;
import org.radargun.config.ComplexConverter;
import org.radargun.config.ComplexDefinition;
import org.radargun.config.Definition;
import org.radargun.config.DefinitionElement;
import org.radargun.config.PropertyHelper;
import org.radargun.config.SimpleDefinition;

/**
 * Converts a elements in definition into list of instances. Instance class
 * is chosen according to element name as specified in {@link org.radargun.config.DefinitionElement#name()}
 * on one of the classes passed in the constructor, or classes implementing class passed to the contructor.
 * In the latter case, all JARs in lib/ are scanned for these implementations annotated with DefinitionElement
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ReflexiveConverters {

   protected static abstract class Base {
      protected final Map<String, Class<?>> classes = new HashMap<String, Class<?>>();

      protected Base(Class<?>[] classes) {
         for (Class<?> clazz : classes) {
            DefinitionElement de = clazz.getAnnotation(DefinitionElement.class);
            if (de == null) {
               throw new IllegalArgumentException(clazz.getName() + " missing @DefinitionElement");
            }
            if (this.classes.containsKey(de.name())) {
               throw new IllegalArgumentException("Trying to register " + clazz.getName() + " as '" + de.name()
                     + "' but this is already used by " + this.classes.get(de.name()));
            }
            this.classes.put(de.name(), clazz);
         }
      }

      protected <T> Base(Class<T> implementedClass) {
         for (File file : Directories.LIB_DIR.listFiles(new Utils.JarFilenameFilter())) {
            for (Class<? extends T> clazz : AnnotatedHelper.getClassesFromJar(file.getPath(), implementedClass, DefinitionElement.class)) {
               DefinitionElement de = clazz.getAnnotation(DefinitionElement.class);
               if (this.classes.containsKey(de.name())) {
                  throw new IllegalArgumentException("Trying to register " + clazz.getName() + " as '" + de.name()
                        + "' but this is already used by " + this.classes.get(de.name()));
               }
               classes.put(de.name(), clazz);
            }
         }
      }

      protected Object instantiate(String name, Definition definition) {
         Class<?> clazz = classes.get(name);
         if (clazz == null) throw new IllegalArgumentException(name);
         Object item;
         try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            item = ctor.newInstance();
         } catch (Exception e) {
            throw new IllegalArgumentException("Cannot instantiate " + clazz.getName(), e);
         }
         DefinitionElement de = clazz.getAnnotation(DefinitionElement.class);
         if (definition instanceof ComplexDefinition) {
            if (de.resolveType() == DefinitionElement.ResolveType.PASS_BY_MAP) {
               PropertyHelper.setPropertiesFromDefinitions(item, ((ComplexDefinition) definition).getAttributeMap(), false, true);
            } else if (de.resolveType() == DefinitionElement.ResolveType.PASS_BY_DEFINITION) {
               PropertyHelper.setPropertiesFromDefinitions(item, Collections.singletonMap("", definition), false, true);
            } else throw new IllegalStateException(de.resolveType().toString());
         } else if (definition instanceof SimpleDefinition) {
            PropertyHelper.setProperties(item, Collections.singletonMap("", ((SimpleDefinition) definition).value), false, true);
         }
         return item;
      }

      public Collection<Class<?>> content() {
         return classes.values();
      }
   }

   public static class ObjectConverter extends Base implements ComplexConverter<Object> {
      public ObjectConverter(Class<?>[] classes) {
         super(classes);
      }

      public <T> ObjectConverter(Class<T> implementedClass) {
         super(implementedClass);
      }

      @Override
      public Object convert(ComplexDefinition definition, Type type) {
         List<ComplexDefinition.Entry> attributes = definition.getAttributes();
         if (attributes.size() != 1) throw new IllegalArgumentException("Single attribute expected");
         return instantiate(attributes.get(0).name, attributes.get(0).definition);
      }

      @Override
      public String convertToString(Object value) {
         if (value == null) return "null";
         DefinitionElement de = value.getClass().getAnnotation(DefinitionElement.class);
         if (de == null) throw new IllegalArgumentException("Object does not have DefinitionElement attached: " + value);
         return String.format("%s -> %s", de.name(), value);
      }

      @Override
      public int minAttributes() {
         return 1;
      }

      @Override
      public int maxAttributes() {
         return 1;
      }
   }

   public static class ListConverter extends Base implements ComplexConverter<List> {
      public ListConverter(Class<?>[] classes) {
         super(classes);
      }

      public <T> ListConverter(Class<T> implementedClass) {
         super(implementedClass);
      }

      @Override
      public List convert(ComplexDefinition definition, Type type) {
         if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            if (!List.class.isAssignableFrom(clazz)) throw new IllegalArgumentException(type.toString());
         } else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) type;
            if (!List.class.isAssignableFrom((Class<?>) ptype.getRawType())) throw new IllegalArgumentException(type.toString());
         }
         List list = new ArrayList();
         for (ComplexDefinition.Entry entry : definition.getAttributes()) {
            list.add(instantiate(entry.name, entry.definition));
         }
         return list;
      }

      @Override
      public String convertToString(List list) {
         StringBuilder sb = new StringBuilder("[ ");
         boolean first = true;
         for (Object item : list) {
            if (!first) {
               sb.append(", ");
            }
            first = false;
            DefinitionElement de = item.getClass().getAnnotation(DefinitionElement.class);
            if (de != null) {
               sb.append(de.name()).append(" = ");
            }
            sb.append(String.valueOf(item));
         }
         return sb.append(" ]").toString();
      }

      @Override
      public int minAttributes() {
         return 0;
      }

      @Override
      public int maxAttributes() {
         return -1;
      }
   }
}
