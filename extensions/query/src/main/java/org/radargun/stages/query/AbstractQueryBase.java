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
import org.radargun.utils.MinMax;

/**
 * Logic for creating the query builders and retrieving query results.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractQueryBase {
   private static final Log log = LogFactory.getLog(QueryBase.class);

   protected Query.Builder[] builders;

   @Property(doc = "Check whether all invocations got the same result, and fail if not. Default is false.")
   private boolean checkSameResult = false;

   @Property(doc = "Number of queries generated. Makes sense only when the conditions contain random data. Default is 1.")
   protected int numQueries = 1;

   @Property(doc = "Full names of the attribute queried from InternalsExposition. Expecting values parse-able as long values. Default are none.")
   private List<String> exposedAttributes = Collections.EMPTY_LIST;

   private AtomicInteger minResultSize = new AtomicInteger(-1);
   private AtomicInteger maxResultSize = new AtomicInteger(-1);

   public QueryBase.Data createQueryData(InternalsExposition internalsExposition) {
      Map<String, String> exposedAttributeValues = new HashMap<>();
      if (internalsExposition != null) {
         for (String attribute : exposedAttributes) {
            internalsExposition.getCustomStatistics(attribute);
         }
      }
      return new QueryBase.Data(minResultSize.get(), maxResultSize.get(), exposedAttributeValues);
   }

   public Query buildQuery(int queryNumber) {
      return builders[queryNumber].build();
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
