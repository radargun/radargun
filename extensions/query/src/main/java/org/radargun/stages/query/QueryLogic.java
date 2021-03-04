package org.radargun.stages.query;

import org.radargun.traits.Queryable;

public class QueryLogic extends AbstractQueryLogic {

   public QueryLogic(QueryBase queryBase, Queryable queryable, boolean useTransactions) {
      super(queryBase, queryable, useTransactions);
   }
}
