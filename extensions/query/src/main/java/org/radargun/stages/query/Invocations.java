package org.radargun.stages.query;

import org.radargun.Operation;
import org.radargun.stages.test.Invocation;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;

public class Invocations {
   public static final class Query implements Invocation<org.radargun.traits.Query.Result> {
      protected static final Operation TX = Queryable.QUERY.derive("TX");
      private final org.radargun.traits.Query query;
      private final org.radargun.traits.Query.Context context;

      public Query(org.radargun.traits.Query query, org.radargun.traits.Query.Context context) {
         this.query = query;
         this.context = context;
      }

      @Override
      public org.radargun.traits.Query.Result invoke() {
         return query.execute(context);
      }

      @Override
      public Operation operation() {
         return Queryable.QUERY;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }
}
