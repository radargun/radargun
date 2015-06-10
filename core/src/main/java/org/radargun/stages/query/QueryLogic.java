package org.radargun.stages.query;

import org.radargun.Operation;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.cache.test.Invocations;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.Stressor;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class QueryLogic extends OperationLogic {
   protected final QueryBase queryBase;
   protected final Log log = LogFactory.getLog(getClass());
   protected final Queryable queryable;
   protected Query.Result previousQueryResult = null;
   protected Query.Context context;

   AtomicIntegerArray queryInvocations;

   public QueryLogic(QueryBase queryBase, Queryable queryable) {
      this.queryBase = queryBase;
      this.queryable = queryable;
      queryInvocations = new AtomicIntegerArray(queryBase.getNumQueries());
   }

   @Override
   public void transactionStarted() {
      if (context != null) {
         stressor.wrap(context);
      }
   }

   @Override
   public void transactionEnded() {
      if (context != null) {
         context.close();
         context = null;
      }
   }

   @Override
   public void init(Stressor stressor) {
      super.init(stressor);
      stressor.setUseTransactions(true);
   }

   @Override
   public void run(Operation ignored) throws RequestException {
      int randomQueryNumber = stressor.getRandom().nextInt(queryBase.getNumQueries());
      Query query = queryBase.builders[randomQueryNumber].build();
      Query.Result queryResult;
      context = queryable.createContext(null);
      long start = System.nanoTime();
      queryResult = (Query.Result) stressor.makeRequest(new Invocations.Query(query, context));
      long end = System.nanoTime();
      log.infof("Invoked query %d (%dth) in %d us", randomQueryNumber, queryInvocations.incrementAndGet(randomQueryNumber), (end - start) / 1000);

      int size = queryResult.size();
      if (previousQueryResult != null) {
         if (queryBase.isCheckSameResult() && size != previousQueryResult.size()) {
            throw new IllegalStateException("The query result is different from the previous one. All results should be the same when executing the same query");
         }
      } else {
         log.info("First result has " + size + " entries");
         if (log.isTraceEnabled()) {
            for (Object entry : queryResult.values()) {
               log.trace(String.valueOf(entry));
            }
         }
         int min = queryBase.getMinResultSize();
         if (queryBase.isCheckSameResult() && min >= 0 && min != size) {
            throw new IllegalStateException("Another thread reported " + min + " results while we have " + size);
         }
         queryBase.updateMinResultSize(size);
         int max = queryBase.getMaxResultSize();
         if (queryBase.isCheckSameResult() && max >= 0 && max != size) {
            throw new IllegalStateException("Another thread reported " + max + " results while we have " + size);
         }
         queryBase.updateMaxResultSize(size);
      }
      previousQueryResult = queryResult;
   }
}
