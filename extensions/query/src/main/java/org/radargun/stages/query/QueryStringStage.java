package org.radargun.stages.query;

import org.radargun.config.Property;
import org.radargun.config.PropertyDelegate;
import org.radargun.config.Stage;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.TestStage;
import org.radargun.stages.test.TransactionMode;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Queryable;

@Stage(doc = "Stage which executes a query created from a String.")
public class QueryStringStage extends TestStage {

   @PropertyDelegate
   public QueryStringBase base = new QueryStringBase();

   @Property(doc = "Query String that will be executed")
   private String queryString;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private Queryable queryable;

   @Override
   public OperationLogic createLogic() {
      boolean useTxs = useTransactions == TransactionMode.ALWAYS ? true : useTransactions == TransactionMode.NEVER ? false : useTransactions(null);
      return new QueryStringLogic(base, queryable, useTxs);
   }

   @Override
   public void init() {
      base.init(queryable, queryString);
   }
}
