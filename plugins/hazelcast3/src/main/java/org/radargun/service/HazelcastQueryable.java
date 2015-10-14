package org.radargun.service;

import com.hazelcast.core.IMap;
import com.hazelcast.mapreduce.aggregation.Aggregation;
import com.hazelcast.mapreduce.aggregation.Aggregations;
import com.hazelcast.mapreduce.aggregation.PropertyExtractor;
import com.hazelcast.mapreduce.aggregation.Supplier;
import com.hazelcast.query.PagingPredicate;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;
import org.radargun.utils.OptimizedMap;
import org.radargun.utils.Projections;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 *
 * Hazelcast has a limited support for aggregations - only a single aggregation can be executed per query, without any grouping,
 * and because of an existing bug, also without filtering:
 * http://stackoverflow.com/questions/29481508/hazelcast-aggregations-api-results-in-classcastexception-with-predicates
 * Additionaly, indexes are not used in aggregations.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Jakub Markoš &lt;jmarkos@redhat.com&gt;
 */
public class HazelcastQueryable implements Queryable {
   private static final Log log = LogFactory.getLog(HazelcastQueryable.class);
   protected final Hazelcast3Service service;

   public HazelcastQueryable(Hazelcast3Service service) {
      this.service = service;
   }

   @Override
   public QueryBuilder getBuilder(String mapName, Class<?> clazz) {
      return new HazelcastQueryBuilder(service.getMap(mapName), clazz);
   }

   @Override
   public void reindex(String containerName) {
      // noop
   }

   private class HazelcastQueryBuilder implements QueryBuilder {
      private final IMap map;
      private final Class<?> clazz;
      private Predicate predicate;
      private Comparator comparator;
      private int limit = -1;
      private int offset = 0;
      private SelectExpression[] projection;

      private HazelcastQueryBuilder(IMap<Object, Object> map, Class<?> clazz) {
         this.map = map;
         this.clazz = clazz;
      }

      @Override
      public QueryBuilder subquery() {
         return new HazelcastQueryBuilder(null, clazz);
      }

      private void implicitAnd(Predicate p) {
         if (predicate == null) {
            predicate = p;
         } else {
            predicate = Predicates.and(predicate, p);
         }
      }

      @Override
      public QueryBuilder eq(SelectExpression selectExpression, Object value) {
         implicitAnd(Predicates.equal(selectExpression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public QueryBuilder lt(SelectExpression selectExpression, Object value) {
         implicitAnd(Predicates.lessThan(selectExpression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public QueryBuilder le(SelectExpression selectExpression, Object value) {
         implicitAnd(Predicates.lessEqual(selectExpression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public QueryBuilder gt(SelectExpression selectExpression, Object value) {
         implicitAnd(Predicates.greaterThan(selectExpression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public QueryBuilder ge(SelectExpression selectExpression, Object value) {
         implicitAnd(Predicates.greaterEqual(selectExpression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public QueryBuilder between(SelectExpression selectExpression, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive) {
         if (!lowerInclusive || !upperInclusive) throw new IllegalArgumentException("Hazelcast supports only inclusive bounds");
         implicitAnd(Predicates.between(selectExpression.attribute, (Comparable) lowerBound, (Comparable) upperBound));
         return this;
      }

      @Override
      public QueryBuilder isNull(SelectExpression selectExpression) {
         implicitAnd(Predicates.equal(selectExpression.attribute, null));
         return this;
      }

      @Override
      public QueryBuilder like(SelectExpression selectExpression, String pattern) {
         implicitAnd(Predicates.like(selectExpression.attribute, pattern));
         return this;
      }

      @Override
      public QueryBuilder contains(SelectExpression selectExpression, Object value) {
         throw new UnsupportedOperationException();
      }

      @Override
      public QueryBuilder not(QueryBuilder subquery) {
         implicitAnd(Predicates.not(((HazelcastQueryBuilder) subquery).predicate));
         return this;
      }

      @Override
      public QueryBuilder any(QueryBuilder... subqueries) {
         Predicate p = null;
         for (QueryBuilder subquery : subqueries) {
            if (p == null) {
               p = ((HazelcastQueryBuilder) subquery).predicate;
            } else {
               p = Predicates.or(p, ((HazelcastQueryBuilder) subquery).predicate);
            }
         }
         implicitAnd(p);
         return this;
      }

      @Override
      public QueryBuilder orderBy(SelectExpression selectExpression, SortOrder order) {
         if (order == SortOrder.DESCENDING) {
            comparator = new InverseComparator(selectExpression.attribute);
         } else {
            comparator = new RegularComparator(selectExpression.attribute);
         }
         return this;
      }

      @Override
      public QueryBuilder projection(SelectExpression... selectExpressions) {
         log.warn("Projection is emulated; no native support for projection.");
         for (SelectExpression selectExpression : selectExpressions) {
            if (selectExpression.function != AggregationFunction.NONE && selectExpressions.length != 1)
               throw new RuntimeException("Hazelcast only supports a single aggregation per query!");
         }
         this.projection = selectExpressions;
         return this;
      }

      @Override
      public QueryBuilder groupBy(String[] attribute) {
         throw new UnsupportedOperationException();
      }

      @Override
      public QueryBuilder offset(long offset) {
         log.warn("Offset is emulated; first records will be loaded anyway.");
         this.offset = (int) offset;
         return this;
      }

      @Override
      public QueryBuilder limit(long limit) {
         this.limit = (int) limit;
         return this;
      }

      @Override
      public Query build() {
         if (projection != null && projection.length == 1 && projection[0].function != AggregationFunction.NONE) {
            return new HazelcastAggregationQuery(map, clazz, predicate, projection[0]);
         }
         Predicate finalPredicate;
         if (comparator == null) {
            if (limit < 0) finalPredicate = predicate;
            else finalPredicate = new PagingPredicate(predicate, limit);
         } else {
            if (limit < 0) finalPredicate = new PagingPredicate(predicate, comparator, Integer.MAX_VALUE);
            else finalPredicate = new PagingPredicate(predicate, comparator, limit);
         }
         String[] stringProjection = new String[projection.length];
         for (int i = 0; i < projection.length; i++) {
            stringProjection[i] = projection[i].attribute;
         }
         return new HazelcastQuery(map, finalPredicate, offset, stringProjection);
      }
   }

   private class HazelcastQuery implements Query {
      private final IMap map;
      private final Predicate predicate;
      private final int offset;
      private final String[] projection;

      public HazelcastQuery(IMap map, Predicate predicate, int offset, String[] projection) {
         this.map = map;
         this.predicate = predicate;
         this.offset = offset;
         this.projection = projection;
      }

      @Override
      public QueryResult execute() {
         if (predicate == null) return new HazelcastQueryResult(map.values(), offset, projection);
         else return new HazelcastQueryResult(map.values(predicate), offset, projection);
      }
   }

   private class HazelcastAggregationQuery implements Query {
      private final IMap map;
      private final PropertyExtractor propertyExtractor;
      Aggregation aggregation;
      Supplier supplier;

      public HazelcastAggregationQuery(IMap map, Class<?> clazz, Predicate predicate, SelectExpression aggregatedAttribute) {
         this.map = map;
         Accessor accessor = getAccessor(clazz, aggregatedAttribute.attribute);
         propertyExtractor = new ReflexivePropertyExtractor(accessor);

         Supplier reflexiveSupplier = Supplier.all(propertyExtractor);
         if (predicate != null) {
            supplier = Supplier.fromPredicate(predicate, reflexiveSupplier);
         } else {
            supplier = reflexiveSupplier;
         }

         AggregationFunction function = aggregatedAttribute.function;
         String type = accessor.getReturnType().getSimpleName();

         aggregation = null;
         if (function == AggregationFunction.NONE)
            throw new IllegalStateException("Aggregated query needs an aggregation!");
         if (function == AggregationFunction.COUNT)
            aggregation = Aggregations.count();
         if (function == AggregationFunction.SUM && (type.equals("Integer") || type.equals("int")))
            aggregation = Aggregations.integerSum();
         if (function == AggregationFunction.SUM && type.equalsIgnoreCase("double"))
            aggregation = Aggregations.doubleSum();
         if (function == AggregationFunction.SUM && type.equalsIgnoreCase("long"))
            aggregation = Aggregations.longSum();
         if (function == AggregationFunction.AVG && (type.equals("Integer") || type.equals("int")))
            aggregation = Aggregations.integerAvg();
         if (function == AggregationFunction.AVG && type.equalsIgnoreCase("double"))
            aggregation = Aggregations.doubleAvg();
         if (function == AggregationFunction.AVG && type.equalsIgnoreCase("long"))
            aggregation = Aggregations.longAvg();
         if (function == AggregationFunction.MAX && (type.equals("Integer") || type.equals("int")))
            aggregation = Aggregations.integerMax();
         if (function == AggregationFunction.MAX && type.equalsIgnoreCase("double"))
            aggregation = Aggregations.doubleMax();
         if (function == AggregationFunction.MAX && type.equalsIgnoreCase("long"))
            aggregation = Aggregations.longMax();
         if (function == AggregationFunction.MAX && type.equals("String"))
            aggregation = Aggregations.comparableMax();
         if (function == AggregationFunction.MIN && (type.equals("Integer") || type.equals("int")))
            aggregation = Aggregations.integerMin();
         if (function == AggregationFunction.MIN && type.equalsIgnoreCase("double"))
            aggregation = Aggregations.doubleMin();
         if (function == AggregationFunction.MIN && type.equalsIgnoreCase("long"))
            aggregation = Aggregations.longMin();
         if (function == AggregationFunction.MIN && type.equals("String"))
            aggregation = Aggregations.comparableMin();
      }

      @Override
      public QueryResult execute() {
         ArrayList result = new ArrayList<>();
         result.add(map.aggregate(supplier, aggregation));
         return new HazelcastQueryResult(result, 0, null);
      }
   }

   private class HazelcastQueryResult implements Query.QueryResult {
      private final Collection values;

      public HazelcastQueryResult(Collection values, int offset, String[] projection) {
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

   private abstract static class ReflexiveComparator implements Comparator<Map.Entry>, Serializable {
      protected transient Map<Class, Accessor> accessors = new OptimizedMap<Class, Accessor>();
      protected final String attribute;

      protected ReflexiveComparator(String attribute) {
         this.attribute = attribute;
      }

      // magic deserialization method
      private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
         in.defaultReadObject();
         accessors = new OptimizedMap<Class, Accessor>();
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

   private static class RegularComparator extends ReflexiveComparator {
      private RegularComparator(String attribute) {
         super(attribute);
      }

      @Override
      protected int compare(Comparable o1, Comparable o2) {
         return o1.compareTo(o2);
      }
   }

   private static class InverseComparator extends ReflexiveComparator {
      public InverseComparator(String attribute) {
         super(attribute);
      }

      @Override
      protected int compare(Comparable o1, Comparable o2) {
         return -o1.compareTo(o2);
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

   private static Accessor getAccessor(Class<?> clazz, String attribute) {
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

      Class<?> getReturnType();

   }

   private static class ReflexivePropertyExtractor<T> implements PropertyExtractor<Object, T> {
      private Accessor accessor;

      public ReflexivePropertyExtractor(Accessor accessor) {
         this.accessor = accessor;
      }

      public T extract(Object value) {
         return (T) accessor.get(value);
      }
   }

   private static class FieldAccessor implements Accessor, Externalizable {

      public Field f;

      public FieldAccessor() {}

      public FieldAccessor(Field f) {
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

      @Override
      public Class<?> getReturnType() {
         return f.getType();
      }

      @Override
      public void writeExternal(ObjectOutput objectOutput) throws IOException {
         objectOutput.writeObject(f.getDeclaringClass());
         objectOutput.writeObject(f.getName());
      }

      @Override
      public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
         try {
            f = ((Class) objectInput.readObject()).getDeclaredField((String) objectInput.readObject());
            f.setAccessible(true);
         } catch (NoSuchFieldException e) {
            e.printStackTrace();
         }
      }
   }

   private static class MethodAccessor implements Accessor, Externalizable {
      public Method m;

      private MethodAccessor() {}

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

      @Override
      public Class<?> getReturnType() {
         return m.getReturnType();
      }

      @Override
      public void writeExternal(ObjectOutput objectOutput) throws IOException {
         objectOutput.writeObject(m.getDeclaringClass());
         objectOutput.writeObject(m.getName());
      }

      @Override
      public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
         try {
            m = ((Class) objectInput.readObject()).getDeclaredMethod((String) objectInput.readObject());
            m.setAccessible(true);
         } catch (NoSuchMethodException e) {
            e.printStackTrace();
         }
      }
   }

   private static class ChainedAccessor implements Accessor, Externalizable {
      public List<Accessor> accessors;

      public ChainedAccessor(){}

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

      @Override
      public Class<?> getReturnType() {
         return accessors.get(accessors.size() - 1).getReturnType();
      }

      @Override
      public void writeExternal(ObjectOutput objectOutput) throws IOException {
         objectOutput.writeObject(accessors);
      }

      @Override
      public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
         accessors = (List<Accessor>) objectInput.readObject();
      }
   }

}
