package org.radargun.stages.query;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;
import org.radargun.utils.MinMax;

/**
 * Logic for creating the query builders and retrieving query results.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class QueryBase {
   private static final Log log = LogFactory.getLog(QueryBase.class);

   @Property(doc = "Check whether all invocations got the same result, and fail if not. Default is false.")
   private boolean checkSameResult = false;

   @Property(doc = "Number of queries generated. Makes sense only when the conditions contain random data. Default is 1.")
   private int numQueries = 1;

   @Property(doc = "Full names of the attribute queried from InternalsExposition. Expecting values parse-able as long values. Default are none.")
   private List<String> exposedAttributes = Collections.EMPTY_LIST;

   protected Query.Builder[] builders;

   private AtomicInteger minResultSize = new AtomicInteger(-1);
   private AtomicInteger maxResultSize = new AtomicInteger(-1);

   public void init(Queryable queryable, QueryConfiguration query) {
      if (queryable == null) {
         return; // called from main, ignore
      }
      builders = new Query.Builder[numQueries];
      for (int i = 0; i < numQueries; ++i) {
         builders[i] = constructBuilder(queryable, query);
      }
   }

   public static Query.Builder constructBuilder(Queryable queryable, QueryConfiguration query) {
      Class<?> clazz;
      try {
         clazz = Class.forName(query.clazz);
      } catch (ClassNotFoundException e) {
         throw new IllegalArgumentException("Cannot load class " + query.clazz, e);
      }
      Query.Builder builder = queryable.getBuilder(null, clazz);
      if (query.conditions != null) {
         for (Condition condition : query.conditions) {
            condition.apply(builder);
         }
      }
      if (query.orderBy != null) {
         for (OrderBy ob : query.orderBy) {
            builder.orderBy(new Query.SelectExpression(ob.attribute, ob.asc));
         }
      } else if (query.orderByAggregatedColumns != null) {
         for (Condition.OrderedSelectExpressionElement orderByAggregatedColumn : query.orderByAggregatedColumns) {
            builder.orderBy(orderByAggregatedColumn.toSelectExpression());
         }
      }
      if (query.projection != null) {
         Query.SelectExpression[] projections = new Query.SelectExpression[query.projection.length];
         for (int i = 0; i < query.projection.length; i++) {
            projections[i] = new Query.SelectExpression(query.projection[i]);
         }
         builder.projection(projections);
      } else if (query.projectionAggregated != null && !query.projectionAggregated.isEmpty()) {
         Query.SelectExpression[] projections = new Query.SelectExpression[query.projectionAggregated.size()];
         for (int i = 0; i < query.projectionAggregated.size(); i++) {
            projections[i] = query.projectionAggregated.get(i).toSelectExpression();
         }
         builder.projection(projections);
      }
      if (query.groupBy != null) {
         builder.groupBy(query.groupBy);
         if (query.having != null) {
            for (Condition condition : query.having) {
               condition.apply(builder);
            }
         }
      }
      if (query.offset >= 0) {
         builder.offset(query.offset);
      }
      if (query.limit >= 0) {
         builder.limit(query.limit);
      }
      return builder;
   }

   public QueryBase.Data createQueryData(InternalsExposition internalsExposition) {
      Map<String, String> exposedAttributeValues = new HashMap<>();
      if (internalsExposition != null) {
         for (String attribute : exposedAttributes) {
            internalsExposition.getCustomStatistics(attribute);
         }
      }
      return new QueryBase.Data(minResultSize.get(), maxResultSize.get(), exposedAttributeValues);
   }

   private long parse(String str) {
      if (str == null) return 0;
      return Long.parseLong(str);
   }

   public void checkAndRecordResults(Map<Integer, Data> results, Report.Test test, int iteration) {
      MinMax.Int totalResultSize = new MinMax.Int();
      Map<Integer, Report.WorkerResult> workerResultSizes = new HashMap<>();
      Map<String, Map<Integer, Report.WorkerResult>> exposedResults = new HashMap<>();
      Map<String, MinMax.Long> exposedAggregations = new HashMap<>();
      for (Map.Entry<Integer, QueryBase.Data> entry : results.entrySet()) {
         int workerIndex = entry.getKey();
         QueryBase.Data data = entry.getValue();

         if (totalResultSize.isSet() && (totalResultSize.min() != data.minResultSize || totalResultSize.max() != data.maxResultSize)) {
            String message = String.format("The size got from %d -> %d .. %d is not the same as from other workers -> %d .. %d ",
               workerIndex, data.minResultSize, data.maxResultSize, totalResultSize.min(), totalResultSize.max());
            if (checkSameResult) {
               throw new IllegalStateException(message);
            } else {
               log.info(message);
            }
         }
         for (String attribute : exposedAttributes) {
            String value = data.exposedAttributeValues.get(attribute);
            if (value == null) continue;
            Map<Integer, Report.WorkerResult> workerResult = exposedResults.get(attribute);
            if (workerResult == null) {
               exposedResults.put(attribute, workerResult = new HashMap<>());
            }
            workerResult.put(workerIndex, new Report.WorkerResult(value, false));
            MinMax.Long aggreg = exposedAggregations.get(attribute);
            if (aggreg == null) {
               exposedAggregations.put(attribute, aggreg = new MinMax.Long());
            }
            try {
               aggreg.add(parse(value));
            } catch (NumberFormatException e) {
               aggreg.add(0);
            }
         }
      }

      if (test != null) {
         test.addResult(iteration, new Report.TestResult("Query result size", workerResultSizes, totalResultSize.toString(), false));
         for (Map.Entry<String, Map<Integer, Report.WorkerResult>> entry : exposedResults.entrySet()) {
            test.addResult(iteration, new Report.TestResult("Exposed: " + entry.getKey(),
               entry.getValue(), exposedAggregations.get(entry.getKey()).toString(), false));
         }
      } else {
         log.info("No test name - results are not recorded");
      }
   }

   public int getMinResultSize() {
      return minResultSize.get();
   }

   public void updateMinResultSize(int size) {
      int min = minResultSize.get();
      while ((min < 0 || min > size) && !minResultSize.compareAndSet(min, size)) {
         min = minResultSize.get();
      }
   }

   public int getMaxResultSize() {
      return maxResultSize.get();
   }

   public void updateMaxResultSize(int size) {
      int max = maxResultSize.get();
      while ((max < 0 || max < size) && !maxResultSize.compareAndSet(max, size)) {
         max = maxResultSize.get();
      }
   }

   public boolean isCheckSameResult() {
      return checkSameResult;
   }

   public int getNumQueries() {
      return numQueries;
   }

   public Query buildQuery(int queryNumber) {
      return builders[queryNumber].build();
   }

   public static class Data implements Serializable {
      public final int minResultSize;
      public final int maxResultSize;
      public final Map<String, String> exposedAttributeValues;

      public Data(int minResultSize, int maxResultSize, Map<String, String> exposedAttributeValues) {
         this.minResultSize = minResultSize;
         this.maxResultSize = maxResultSize;
         this.exposedAttributeValues = exposedAttributeValues;
      }
   }
}
