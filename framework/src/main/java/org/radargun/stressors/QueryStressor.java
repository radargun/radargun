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

import org.radargun.config.Property;
import org.radargun.config.Stressor;
import org.radargun.features.Queryable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stressor for Local cache mode, which will execute queries on the cache.
 *
 * @author Anna Manukyan
 */
@Stressor(doc = "Executes queries using infinispan-query API against the cache wrapper.")
public class QueryStressor extends StressTestStressor {
   @Property(optional = false, doc = "Boolean variable which shows whether the keyword query should be done or wildcard.")
   private boolean isWildcardQuery;

   @Property(optional = false, doc = "The name of the field for which the query should be executed.")
   private String onField;

   @Property(optional = false, doc = "The matching string which should be used for querying.")
   private String matching;

   private List<Integer> resultObjects = null;

   public OperationLogic getLogic() {
      return new QueryRunnerLogic();
   }

   protected class QueryRunnerLogic implements OperationLogic {

      @Override
      public void init(String bucketId, int threadIndex) {
         resultObjects = new ArrayList();
      }

      @Override
      public Object run(Stressor stressor, int iteration) {
         Map<String, Object> paramMap = new HashMap<String, Object>();
         paramMap.put(Queryable.QUERYABLE_FIELD, onField);
         paramMap.put(Queryable.MATCH_STRING, matching);
         paramMap.put(Queryable.IS_WILDCARD, isWildcardQuery);

         List obj = (List) stressor.makeRequest(iteration, Operation.QUERY, paramMap);
         resultObjects.add(obj.size());

         return obj;
      }
   }

   protected Map<String, Object> processResults() {
      compareQueryResults();

      return super.processResults();
   }

   private void compareQueryResults() {
      int previousResult = -1;
      for (Integer queryResult : resultObjects) {
         if (previousResult > 0) {
            assert queryResult == previousResult : "The results are not the same for all queries.";
         }

         previousResult = queryResult;
      }
   }

   @Override
   public String toString() {
      return "QueryStressor{" +
            "isWildcardQuery=" + isWildcardQuery +
            ", onField=" + onField +
            ", matching=" + matching +
            "}";
   }
}
