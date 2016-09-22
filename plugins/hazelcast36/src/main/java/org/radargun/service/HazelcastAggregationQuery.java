package org.radargun.service;

import java.util.ArrayList;

import com.hazelcast.mapreduce.aggregation.Aggregation;
import com.hazelcast.mapreduce.aggregation.Aggregations;
import com.hazelcast.mapreduce.aggregation.PropertyExtractor;
import com.hazelcast.mapreduce.aggregation.Supplier;
import com.hazelcast.query.Predicate;
import org.radargun.traits.Query;

import static org.radargun.service.HazelcastQuery.getAccessor;

public class HazelcastAggregationQuery implements Query {
   private final PropertyExtractor propertyExtractor;
   Aggregation aggregation;
   Supplier supplier;

   public HazelcastAggregationQuery(Class<?> clazz, Predicate predicate, SelectExpression aggregatedAttribute) {
      HazelcastQuery.Accessor accessor = getAccessor(clazz, aggregatedAttribute.attribute);
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
   public Query.Result execute(Context context) {
      HazelcastQuery.Context impl = (HazelcastQuery.Context) context;
      ArrayList result = new ArrayList<>();
      result.add(impl.map.aggregate(supplier, aggregation));
      return new HazelcastQuery.Result(result, 0, null);
   }

   private static class ReflexivePropertyExtractor<T> implements PropertyExtractor<Object, T> {
      private HazelcastQuery.Accessor accessor;

      public ReflexivePropertyExtractor(HazelcastQuery.Accessor accessor) {
         this.accessor = accessor;
      }

      public T extract(Object value) {
         return (T) accessor.get(value);
      }
   }

}