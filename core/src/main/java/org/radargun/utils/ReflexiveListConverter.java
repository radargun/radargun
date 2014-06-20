package org.radargun.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.config.ComplexConverter;
import org.radargun.config.ComplexDefinition;
import org.radargun.config.DefinitionElement;
import org.radargun.config.PropertyHelper;
import org.radargun.config.SimpleDefinition;

/**
 * Converts a elements in definition into list of instances. Instance class
 * is chosen according to element name as specified in {@link org.radargun.config.DefinitionElement#name()}
 * on one of the classes passed in the constructor.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ReflexiveListConverter implements ComplexConverter<List> {
   private final Map<String, Class<?>> classes = new HashMap<String, Class<?>>();

   public ReflexiveListConverter(Class<?>[] classes) {
      for (Class<?> clazz : classes) {
         DefinitionElement de = clazz.getAnnotation(DefinitionElement.class);
         if (de == null) {
            throw new IllegalArgumentException(clazz.getName());
         }
         if (this.classes.containsKey(de.name())) throw new IllegalArgumentException(clazz.getName());
         this.classes.put(de.name(), clazz);
      }
   }

   @Override
   public List convert(ComplexDefinition definition, Type type) {
      if (type instanceof Class<?>) {
         Class<?> clazz = (Class<?>) type;
         if (clazz != List.class) throw new IllegalArgumentException(type.toString());
      } else if (type instanceof ParameterizedType) {
         ParameterizedType ptype = (ParameterizedType) type;
         if (ptype.getRawType() != List.class) throw new IllegalArgumentException(type.toString());
      }
      List list = new ArrayList();
      for (ComplexDefinition.Entry entry : definition.getAttributes()) {
         Class<?> clazz = classes.get(entry.name);
         if (clazz == null) throw new IllegalArgumentException(entry.name);
         Object item;
         try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            item = ctor.newInstance();
         } catch (Exception e) {
            throw new IllegalArgumentException("Cannot instantiate " + clazz.getName(), e);
         }
         DefinitionElement de = clazz.getAnnotation(DefinitionElement.class);
         if (entry.definition instanceof ComplexDefinition) {
            if (de.resolveType() == DefinitionElement.ResolveType.PASS_BY_MAP) {
               PropertyHelper.setPropertiesFromDefinitions(item, ((ComplexDefinition) entry.definition).getAttributeMap(), false, true);
            } else if (de.resolveType() == DefinitionElement.ResolveType.PASS_BY_DEFINITION) {
               PropertyHelper.setPropertiesFromDefinitions(item, Collections.singletonMap("", entry.definition), false, true);
            } else throw new IllegalStateException(de.resolveType().toString());
         } else if (entry.definition instanceof SimpleDefinition) {
            PropertyHelper.setProperties(item, Collections.singletonMap("", ((SimpleDefinition) entry.definition).value), false, true);
         }

         list.add(item);
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
   public Collection<Class<?>> content() {
      return classes.values();
   }
}
