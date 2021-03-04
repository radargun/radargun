package org.radargun.stages.query;

import org.radargun.traits.Query;
import org.radargun.traits.Queryable;

public class QueryStringBase extends AbstractQueryBase {

   public void init(Queryable queryable, String queryString) {
      if (queryable == null) {
         return; // called from main, ignore
      }
      builders = new Query.Builder[numQueries];
      for (int i = 0; i < numQueries; ++i) {
         builders[i] = queryable.getBuilder(null, queryString);
      }
   }
}
