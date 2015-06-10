package org.radargun.service;

import com.hazelcast.core.IMap;
import com.hazelcast.core.TransactionalMap;
import com.hazelcast.query.Predicate;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Query;
import org.radargun.utils.OptimizedMap;
import org.radargun.utils.Projections;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class HazelcastQuery implements Query {
   private static final Log log = LogFactory.getLog(HazelcastQuery.class);

   private final Predicate predicate;
   private final int offset;
   private final String[] projection;

   public HazelcastQuery(Predicate predicate, int offset, String[] projection) {
      this.predicate = predicate;
      this.offset = offset;
      this.projection = projection;
   }

   @Override
   public Query.Result execute(Query.Context context) {
      Context impl = (Context) context;
      Collection values;
      if (predicate == null) {
         values = impl.map != null ? impl.map.values() : impl.txMap.values();
      } else {
         values = impl.map != null ? impl.map.values(predicate) : impl.txMap.values(predicate);
      }
      return new Result(values, offset, projection);
   }

   public Predicate getPredicate() {
      return predicate;
   }

   public static class Context implements Query.Context {
      protected final IMap map;
      protected final TransactionalMap txMap;

      public Context(IMap map) {
         this.map = map;
         this.txMap = null;
      }

      public Context(TransactionalMap map) {
         this.map = null;
         this.txMap = map;
      }

      @Override
      public void close() {
      }
   }

   public static class Result implements Query.Result {
      private final Collection values;

      public Result(Collection values, int offset, String[] projection) {
         if (offset > 0) {
            values = Projections.subset(values, offset, Integer.MAX_VALUE);
         }
         if (projection != null) {
            values = Projections.project(values, new ReflectionProjector(projection));
         }
         this.values = values;
      }

      @Override
      public int size() {
         return values.size();
      }

      @Override
      public Collection values() {
         return Collections.unmodifiableCollection(values);
      }
   }

   private static class ReflectionProjector implements Projections.Func {
      private final String[] projection;
      private transient Map<Class<?>, ArrayList<Accessor>> accessorMap = new OptimizedMap<Class<?>, ArrayList<Accessor>>();

      public ReflectionProjector(String[] projection) {
         this.projection = projection;
      }

      // magic deserialization method
      private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
         in.readObject();
         accessorMap = new OptimizedMap<Class<?>, ArrayList<Accessor>>();
      }

      @Override
      public Object project(Object o) {
         Class<?> clazz = o.getClass();
         ArrayList<Accessor> accessors = accessorMap.get(clazz);
         if (accessors == null) {
            accessors = new ArrayList<Accessor>();
            for (String attribute : projection) {
               accessors.add(getAccessor(clazz, attribute));
            }
            accessorMap.put(clazz, accessors);
         }
         Object[] projected = new Object[projection.length];
         int i = 0;
         for (Accessor accessor : accessors) {
            projected[i] = accessor.get(o);
            ++i;
         }
         return projected;
      }
   }

   public static Accessor getAccessor(Class<?> clazz, String attribute) {
      try {
         ArrayList<Accessor> list = new ArrayList<Accessor>();
         for (String attributePart : attribute.split("\\.")) {
            Field f = clazz.getDeclaredField(attribute);
            if (f != null) {
               f.setAccessible(true);
               list.add(new FieldAccessor(f));
               clazz = f.getType();
               continue;
            }
            Method m = clazz.getDeclaredMethod("get" + Character.toUpperCase(attributePart.charAt(0)) + attributePart.substring(1));
            if (m == null) {
               m = clazz.getMethod("is" + Character.toUpperCase(attributePart.charAt(0)) + attributePart.substring(1));
            }
            if (m != null) {
               m.setAccessible(true);
               list.add(new MethodAccessor(m));
               clazz = m.getReturnType();
               continue;
            }
            throw new IllegalArgumentException("Cannot find attribute part " + attributePart + " in " + clazz);
         }
         if (list.size() == 1) return list.get(0);
         else return new ChainedAccessor(list);
      } catch (Exception e) {
         log.debug("Cannot access attribute " + attribute, e);
         throw new RuntimeException(e);
      }
   }

   private interface Accessor {
      Object get(Object o);
   }

   private static class FieldAccessor implements Accessor {
      private final Field f;

      private FieldAccessor(Field f) {
         this.f = f;
      }

      @Override
      public Object get(Object o) {
         try {
            return f.get(o);
         } catch (IllegalAccessException e) {
            log.debug("Cannot access field " + f.getDeclaringClass() + "." + f.getName(), e);
            throw new RuntimeException(e);
         }
      }
   }

   private static class MethodAccessor implements Accessor {
      private final Method m;

      private MethodAccessor(Method m) {
         this.m = m;
      }

      @Override
      public Object get(Object o) {
         try {
            return m.invoke(o);
         } catch (Exception e) {
            log.debug("Cannot invoke method " + m.getDeclaringClass() + "." + m.getName(), e);
            throw new RuntimeException(e);
         }
      }
   }

   private static class ChainedAccessor implements Accessor {
      private final List<Accessor> accessors;

      public ChainedAccessor(List<Accessor> list) {
         this.accessors = list;
      }

      @Override
      public Object get(Object o) {
         for (Accessor a : accessors) {
            o = a.get(o);
         }
         return o;
      }
   }

   abstract static class ReflexiveComparator implements Comparator<Map.Entry>, Serializable {
      protected transient Map<Class, Accessor> accessors = new OptimizedMap<>();
      protected final String attribute;

      protected ReflexiveComparator(String attribute) {
         this.attribute = attribute;
      }

      // magic deserialization method
      private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
         in.defaultReadObject();
         accessors = new OptimizedMap<>();
      }

      @Override
      public int compare(Map.Entry e1, Map.Entry e2) {
         try {
            Comparable o1 = (Comparable) extractValue(e1.getValue());
            Comparable o2 = (Comparable) extractValue(e2.getValue());
            return compare(o1, o2);
         } catch (Exception e) {
            throw new IllegalArgumentException("Cannot extract " + attribute + " from " + e1.getValue() + " or " + e2.getValue(), e);
         }
      }

      private Object extractValue(Object o) {
         Class<?> clazz = o.getClass();
         Accessor accessor = accessors.get(clazz);
         if (accessor == null) {
            accessors.put(clazz, accessor = getAccessor(clazz, attribute));
         }
         return accessor.get(o);
      }

      protected abstract int compare(Comparable o1, Comparable o2);
   }

   static class RegularComparator extends ReflexiveComparator {
      public RegularComparator(String attribute) {
         super(attribute);
      }

      @Override
      protected int compare(Comparable o1, Comparable o2) {
         return o1.compareTo(o2);
      }
   }

   static class InverseComparator extends ReflexiveComparator {
      public InverseComparator(String attribute) {
         super(attribute);
      }

      @Override
      protected int compare(Comparable o1, Comparable o2) {
         return -o1.compareTo(o2);
      }
   }
}
