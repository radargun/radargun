package org.radargun.stages.cache.test;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.Stressor;
import org.radargun.state.SlaveState;
import org.radargun.stats.Statistics;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;
import org.radargun.utils.Projections;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes Queries using Infinispan-Query API against the cache.
 *
 * @author Anna Manukyan
 */
@Stage(doc = "Stage which executes a Query using Infinispan-query API against all keys in the cache.")
public class QueryStage extends AbstractQueryStage {

   @Override
   protected DistStageAck newStatisticsAck(List<Stressor> stressors) {
      return new QueryAck(slaveState, gatherResults(stressors, new StatisticsResultRetriever()), expectedSize.get());
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      int minSize = Integer.MAX_VALUE, maxSize = Integer.MIN_VALUE;
      Map<Integer, Report.SlaveResult> slaveResults = new HashMap<Integer, Report.SlaveResult>();
      for (QueryAck ack : Projections.instancesOf(acks, QueryAck.class)) {
         if (maxSize >= 0 && (minSize != ack.queryResultSize || maxSize != ack.queryResultSize)) {
            String message = String.format("The size got from %d -> %d is not the same as from other slaves -> %d .. %d ",
                  ack.getSlaveIndex(), ack.queryResultSize, minSize, maxSize);
            if (checkSameResult) {
               log.error(message);
               return errorResult();
            } else {
               log.info(message);
            }
         }
         minSize = Math.min(minSize, ack.queryResultSize);
         maxSize = Math.max(maxSize, ack.queryResultSize);
         slaveResults.put(ack.getSlaveIndex(), new Report.SlaveResult(String.valueOf(ack.queryResultSize), false));
      }
      Report.Test test = getTest(true); // the test was already created in super.processAckOnMaster
      if (test != null) {
         String sizeString = minSize == maxSize ? String.valueOf(maxSize) : String.format("%d .. %d", minSize, maxSize);
         test.addResult(getTestIteration(), new Report.TestResult("Query result size", slaveResults, sizeString, false));
      } else {
         log.info("No test name - results are not recorded");
      }
      return result;
   }

   protected static class QueryAck extends StatisticsAck {
      public final int queryResultSize;

      public QueryAck(SlaveState slaveState, List<List<Statistics>> iterations, int queryResultSize) {
         super(slaveState, iterations);
         this.queryResultSize = queryResultSize;
      }
   }

   @Override
   public OperationLogic getLogic() {
      return new Logic();
   }

   protected class Logic extends OperationLogic {
      protected Queryable.QueryBuilder builder;
      protected Query.QueryResult previousQueryResult = null;

      @Override
      public void init(Stressor stressor) {
         super.init(stressor);
         Class<?> clazz;
         try {
            clazz = slaveState.getClassLoader().loadClass(queryObjectClass);
         } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot load class " + queryObjectClass, e);
         }
         builder = queryable.getBuilder(null, clazz);
         if (conditions != null) {
            for (Condition condition : conditions) {
               condition.apply(builder);
            }
         }
         if (orderBy != null) {
            for (SortElement se : orderBy) {
               builder.orderBy(new Queryable.SelectExpression(se.attribute), se.asc ? Queryable.SortOrder.ASCENDING : Queryable.SortOrder.DESCENDING);
            }
         }
         if (projection != null) {
            Queryable.SelectExpression[] projections = new Queryable.SelectExpression[projection.length];
            for (int i = 0; i < projection.length; i++) {
               projections[i] = new Queryable.SelectExpression(projection[i]);
            }
            builder.projection(projections);
         } else if (aggregatedProjection != null && !aggregatedProjection.isEmpty()) {
            Queryable.SelectExpression[] projections = new Queryable.SelectExpression[aggregatedProjection.size()];
            for (int i = 0; i < aggregatedProjection.size(); i++) {
               projections[i] = aggregatedProjection.get(i).toSelectExpression();
            }
            builder.projection(projections);
         }
         if (groupBy != null) {
            builder.groupBy(groupBy);
            if (having != null) {
               for (Condition condition : having) {
                  condition.apply(builder);
               }
            }
         }
         if (offset >= 0) {
            builder.offset(offset);
         }
         if (limit >= 0) {
            builder.limit(limit);
         }
      }

      @Override
      public Object run() throws RequestException {
         Query query = builder.build();
         Query.QueryResult queryResult = (Query.QueryResult) stressor.makeRequest(new Invocations.Query(query));

         if (previousQueryResult != null) {
            if (queryResult.size() != previousQueryResult.size()) {
               throw new IllegalStateException("The query result is different from the previous one. All results should be the same when executing the same query");
            }
         } else {
            log.info("First result has " + queryResult.size() + " entries");
            if (log.isTraceEnabled()) {
               for (Object entry : queryResult.values()) {
                  log.trace(String.valueOf(entry));
               }
            }
            if (!expectedSize.compareAndSet(-1, queryResult.size())) {
               if (expectedSize.get() != queryResult.size()) {
                  throw new IllegalStateException("Another thread reported " + expectedSize.get() + " results while we have " + queryResult.size());
               }
            }
         }
         previousQueryResult = queryResult;

         return queryResult;
      }
   }

}
