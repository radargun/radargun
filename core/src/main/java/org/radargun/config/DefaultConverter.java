package org.radargun.config;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.radargun.utils.Tokenizer;

/**
 * Default converter that can print or parse any common object
 */
public class DefaultConverter implements Converter<Object> {
   private static final Map<Class<?>, Parser> parserMap;

   static {
      Map<Class<?>, Parser> definedMap = new HashMap<Class<?>, Parser>();
      definedMap.put(String.class, new Parser() {
         @Override
         public Object parse(String string, Type[] parameters) {
            return string;
         }
      });

      definedMap.put(int.class, new Parser() {
         @Override
         public Object parse(String string, Type[] parameters) {
            return Integer.parseInt(string.trim());
         }
      });
      definedMap.put(Integer.class, definedMap.get(int.class));

      definedMap.put(long.class, new Parser() {
         @Override
         public Object parse(String string, Type[] parameters) {
            return Long.parseLong(string.trim());
         }
      });
      definedMap.put(Long.class, definedMap.get(long.class));

      definedMap.put(boolean.class, new Parser() {
         @Override
         public Object parse(String string, Type[] parameters) {
            return Boolean.parseBoolean(string.trim());
         }
      });
      definedMap.put(Boolean.class, definedMap.get(boolean.class));

      definedMap.put(double.class, new Parser() {
         @Override
         public Object parse(String string, Type[] parameters) {
            return Double.parseDouble(string.trim());
         }
      });
      definedMap.put(Double.class, definedMap.get(double.class));

      definedMap.put(Set.class, new Parser() {
         @Override
         public Object parse(String string, Type[] parameters) {
            if (parameters.length != 1) throw new IllegalArgumentException();
            Set set = new HashSet();
            for (Object o : parseCollection(string, parameters[0])) {
               set.add(o);
            }
            return set;
         }
      });
      definedMap.put(List.class, new Parser() {
         @Override
         public Object parse(String string, Type[] parameters) {
            if (parameters.length != 1) throw new IllegalArgumentException();
            return parseCollection(string, parameters[0]);
         }
      });

      /*
       * Superclasses and interfaces may be parsed by parsers for inheriting classes as well.
       *  This approach has some flaws with generics.
       */
      Map<Class<?>, Parser> completionMap = new HashMap<Class<?>, Parser>(definedMap);
      for (;;) {
         for (Map.Entry<Class<?>, Parser> entry : definedMap.entrySet()) {
            Class<?> superclazz = entry.getKey().getSuperclass();
            if (superclazz != null && !completionMap.containsKey(superclazz)) {
               completionMap.put(superclazz, entry.getValue());
            }
            for (Class<?> iface : entry.getKey().getInterfaces()) {
               if (iface != null && !completionMap.containsKey(iface)) {
                  completionMap.put(iface, entry.getValue());
               }
            }
         }
         if (definedMap.size() == completionMap.size()) break;
         definedMap = completionMap;
         completionMap = new HashMap<Class<?>, Parser>(definedMap);
      }
      parserMap = completionMap;
   }

   @Override
   public Object convert(String string, Type type) {
      return staticConvert(string, type);
   }

   public static Object staticConvert(String string, Type type) {
      if (type instanceof Class<?>) {
         Class<?> clazz = (Class<?>) type;
         if (clazz.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) clazz, string);
         }
         Parser parser = parserMap.get(clazz);
         if (parser == null) throw new IllegalArgumentException("Unable to parse '" + string + "' as type " + type);
         return parser.parse(string, null);
      } else if (type instanceof ParameterizedType) {
         ParameterizedType pt = (ParameterizedType) type;
         if (!(pt.getRawType() instanceof Class<?>)) throw new IllegalArgumentException("Raw type expected");
         Parser parser = parserMap.get((Class<?>) pt.getRawType());
         if (parser == null) throw new IllegalArgumentException("Unable to parse '" + string + "' as type " + type);
         return parser.parse(string, pt.getActualTypeArguments());
      } else if (type instanceof GenericArrayType) {
         GenericArrayType gt = (GenericArrayType) type;
         List<Object> list = parseCollection(string, gt.getGenericComponentType());
         Object array = Array.newInstance((Class<?>) gt.getGenericComponentType(), list.size());
         int i = 0;
         for (Object o : list) {
            Array.set(array, i++, o);
         }
         return array;
      } else {
         throw new IllegalArgumentException("Unable to parse '" + string + "' as type " + type);
      }
   }

   private static List<Object> parseCollection(String string, Type type) {
      if (type.equals(Integer.class) || type.equals(int.class)) {
         return parseIntCollection(string, type);
      }
      StringTokenizer tokenizer = new StringTokenizer(string, ",[]", true);
      List list = new ArrayList<Object>();
      StringBuilder innerCollection = null;
      int bracketLevel = 0;
      while (tokenizer.hasMoreTokens()) {
         String token = tokenizer.nextToken().trim();
         if (token.equals(",")) {
            if (innerCollection != null) innerCollection.append(token);
         } else if (token.equals("[")) {
            if (bracketLevel == 0) {
               innerCollection = new StringBuilder();
               bracketLevel++;
            } else {
               innerCollection.append(token);
            }
         } else if (token.equals("]")) {
            bracketLevel--;
            if (bracketLevel < 0) {
               throw new IllegalArgumentException(string);
            } else if (bracketLevel > 0) {
               innerCollection.append(token);
            } else {
               list.add(staticConvert(innerCollection.toString(), type));
            }
         } else {
            if (innerCollection == null) {
               list.add(staticConvert(token, type));
            } else {
               innerCollection.append(token);
            }
         }
      }
      return list;
   }

   /* Includes special handling for .. operator, but lacks brackets handling as int collection shouldn't contain
    * any sub-collection. */
   private static List<Object> parseIntCollection(String string, Type type) {
      Tokenizer tokenizer = new Tokenizer(string.trim(), new String[] { ",", ".."}, true, false, -1);
      List list = new ArrayList<Object>();

      Integer lastNumber = null;
      boolean generateRange = false;
      boolean firstToken = true;
      while (tokenizer.hasMoreTokens()) {
         String token = tokenizer.nextToken().trim();
         if (token.equals(",")) {
            if (firstToken) throw new IllegalArgumentException("Unexpected ',': " + string);
         } else if (token.equals("..")) {
            if (firstToken) throw new IllegalArgumentException("Unexpected '..': " + string);
            generateRange = true;
         } else if (token.isEmpty()) {
            continue;
         } else {
            int value = Integer.parseInt(token);
            if (generateRange) {
               if (lastNumber == null) throw new IllegalArgumentException("Cannot generate range: " + string);
               for (int i = lastNumber + 1; i <= value; ++i) {
                  list.add(i);
               }
            } else {
               lastNumber = value;
               list.add(value);
            }
         }
         firstToken = false;
      }
      return list;
   }

   @Override
   public String convertToString(Object value) {
      if (value instanceof Collection) {
         StringBuilder sb = new StringBuilder("[ ");
         for (Iterator iterator = ((Collection) value).iterator(); iterator.hasNext(); ) {
            sb.append(convertToString(iterator.next()));
            if (iterator.hasNext()) sb.append(", ");
         }
         return sb.append(" ]").toString();
      }
      return String.valueOf(value);
   }

   @Override
   public String allowedPattern(Type type) {
      return null; // the pattern is not used for default converter
   }

   private static interface Parser {
      Object parse(String string, Type[] parameters);
   }

   public static ParameterizedType parametrized(Class<?> clazz, Type... typeParams) {
      return new ParametrizedTypeImpl(clazz, typeParams);
   }

   private static class ParametrizedTypeImpl implements ParameterizedType {

      private Class<?> raw;
      private Type[] typeParams;

      public ParametrizedTypeImpl(Class<?> clazz, Type[] typeParams) {
         raw = clazz;
         this.typeParams = typeParams;
      }

      @Override
      public Type[] getActualTypeArguments() {
         return typeParams;
      }

      @Override
      public Type getRawType() {
         return raw;
      }

      @Override
      public Type getOwnerType() {
         return null;
      }
   }
}
