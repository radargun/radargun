/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stressors;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.config.Property;
import org.radargun.config.Stressor;
import org.radargun.features.Queryable;
import org.radargun.state.SlaveState;

/**
 * Stressor for Local cache mode, which will execute queries on the cache.
 *
 * @author Anna Manukyan
 */
@Stressor(doc = "Executes queries using infinispan-query API against the cache wrapper.")
public class QueryStressor extends StressTestStressor {
   private static final Log log = LogFactory.getLog(QueryStressor.class);

   @Property(optional = false, doc = "Boolean variable which shows whether the keyword query should be done or wildcard.")
   private boolean isWildcardQuery;

   @Property(optional = false, doc = "The name of the field for which the query should be executed.")
   private String onField;

   private Queryable.QueryResult previousQueryResult = null;
   private String matchingWord = null;

   public QueryStressor() {
   }

   public QueryStressor(final SlaveState slaveState) {
      this.slaveState = slaveState;
   }

   @Override
   protected void init(CacheWrapper wrapper) {
      this.matchingWord = (String) slaveState.get(DataForQueryStressor.MATCH_WORD_PROP_NAME);
      super.init(wrapper);
   }

   public OperationLogic getLogic() {
      return new QueryRunnerLogic();
   }

   protected class QueryRunnerLogic implements OperationLogic {

      @Override
      public void init(String bucketId, int threadIndex) {
      }

      @Override
      public Object run(Stressor stressor) throws RequestException {
         Map<String, Object> paramMap = new HashMap<String, Object>();
         paramMap.put(Queryable.QUERYABLE_FIELD, onField);
         paramMap.put(Queryable.MATCH_STRING, matchingWord);
         paramMap.put(Queryable.IS_WILDCARD, isWildcardQuery);

         Queryable.QueryResult queryResult = (Queryable.QueryResult) stressor.makeRequest(Operation.QUERY, paramMap);

         if (previousQueryResult != null) {
            if (queryResult.size() != previousQueryResult.size()) {
               throw new RuntimeException("The query result is different from the previous one. All results should be the same when executing the same query");
            }
         }
         previousQueryResult = queryResult;

         return queryResult;
      }
   }

   protected Map<String, Object> processResults() {
      return super.processResults();
   }

   @Override
   public String toString() {
      return "QueryStressor{" +
            "isWildcardQuery=" + isWildcardQuery +
            ", onField=" + onField +
            ", matching=" + matchingWord +
            "}";
   }
}
