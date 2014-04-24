package org.radargun.stages.cache.stresstest;

import java.util.HashMap;
import java.util.Map;

import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Queryable;

/**
 * Executes Queries using Infinispan-Query API against the cache.
 *
 * @author Anna Manukyan
 */
@Stage(doc = "Stage which executes a Query using Infinispan-query API against all keys in the cache.")
public class QueryStage extends StressTestStage {

   @Property(optional = false, doc = "Boolean variable which shows whether the keyword query should be done or wildcard.")
   private boolean isWildcardQuery;

   @Property(optional = false, doc = "The name of the field for which the query should be executed.")
   private String onField;

   @InjectTrait
   private Queryable queryable;

   private transient Queryable.QueryResult previousQueryResult = null;
   private transient String matchingWord = null;

   @Init
   public void init() {
      this.matchingWord = (String) slaveState.get(DataForQueryStage.MATCH_WORD_PROP_NAME);
   }

   @Override
   protected Stressor createStressor(int threadIndex) {
      Stressor stressor = super.createStressor(threadIndex);
      stressor.setQueryable(queryable);
      return stressor;
   }

   @Override
   public OperationLogic getLogic() {
      return new QueryRunnerLogic();
   }

   protected class QueryRunnerLogic implements OperationLogic {
      @Override
      public void init(int threadIndex, int nodeIndex, int numNodes) {
      }

      @Override
      public Object run(Stressor stressor) throws RequestException {
         Map<String, Object> paramMap = new HashMap<String, Object>();
         paramMap.put(Queryable.QUERYABLE_FIELD, onField);
         paramMap.put(Queryable.MATCH_STRING, matchingWord);
         paramMap.put(Queryable.IS_WILDCARD, isWildcardQuery);

         Queryable.QueryResult queryResult = (Queryable.QueryResult) stressor.makeRequest(Queryable.QUERY, paramMap);

         if (previousQueryResult != null) {
            if (queryResult.size() != previousQueryResult.size()) {
               throw new RuntimeException("The query result is different from the previous one. All results should be the same when executing the same query");
            }
         }
         previousQueryResult = queryResult;

         return queryResult;
      }
   }
}
