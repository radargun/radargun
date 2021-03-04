package org.radargun.stages.query;

import org.radargun.traits.Queryable;

public class QueryStringLogic extends AbstractQueryLogic {

   public QueryStringLogic(QueryStringBase queryBase, Queryable queryable, boolean useTransactions) {
      super(queryBase, queryable, useTransactions);
   }
}
