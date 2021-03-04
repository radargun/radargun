package org.radargun.stages.query;

import org.radargun.traits.Query;
import org.radargun.traits.Queryable;

/**
 * Logic for creating the query builders and retrieving query results.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class QueryBase extends AbstractQueryBase {

   public void init(Queryable queryable, QueryConfiguration query) {
      if (queryable == null) {
         return; // called from main, ignore
      }
      builders = new Query.Builder[numQueries];
      for (int i = 0; i < numQueries; ++i) {
         builders[i] = constructBuilder(queryable, query);
      }
   }

   public static Query.Builder constructBuilder(Queryable queryable, QueryConfiguration query) {
      Class<?> clazz;
      try {
         clazz = Class.forName(query.clazz);
      } catch (ClassNotFoundException e) {
         throw new IllegalArgumentException("Cannot load class " + query.clazz, e);
      }
      Query.Builder builder = queryable.getBuilder(null, clazz);
      if (query.conditions != null) {
         for (Condition condition : query.conditions) {
            condition.apply(builder);
         }
      }
      if (query.orderBy != null) {
         for (OrderBy ob : query.orderBy) {
            builder.orderBy(new Query.SelectExpression(ob.attribute, ob.asc));
         }
      } else if (query.orderByAggregatedColumns != null) {
         for (Condition.OrderedSelectExpressionElement orderByAggregatedColumn : query.orderByAggregatedColumns) {
            builder.orderBy(orderByAggregatedColumn.toSelectExpression());
         }
      }
      if (query.projection != null) {
         Query.SelectExpression[] projections = new Query.SelectExpression[query.projection.length];
         for (int i = 0; i < query.projection.length; i++) {
            projections[i] = new Query.SelectExpression(query.projection[i]);
         }
         builder.projection(projections);
      } else if (query.projectionAggregated != null && !query.projectionAggregated.isEmpty()) {
         Query.SelectExpression[] projections = new Query.SelectExpression[query.projectionAggregated.size()];
         for (int i = 0; i < query.projectionAggregated.size(); i++) {
            projections[i] = query.projectionAggregated.get(i).toSelectExpression();
         }
         builder.projection(projections);
      }
      if (query.groupBy != null) {
         builder.groupBy(query.groupBy);
         if (query.having != null) {
            for (Condition condition : query.having) {
               condition.apply(builder);
            }
         }
      }
      if (query.offset >= 0) {
         builder.offset(query.offset);
      }
      if (query.limit >= 0) {
         builder.limit(query.limit);
      }
      return builder;
   }
}
