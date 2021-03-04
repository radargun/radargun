package org.radargun.service;

import java.util.Comparator;

import com.hazelcast.query.PagingPredicate;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;

/**
 * Hazelcast has a limited support for aggregations - only a single aggregation can be executed per query, without any grouping,
 * and because of an existing bug, also without filtering:
 * http://stackoverflow.com/questions/29481508/hazelcast-aggregations-api-results-in-classcastexception-with-predicates
 * Additionaly, indexes are not used in aggregations.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Jakub Markos &lt;jmarkos@redhat.com&gt;
 */
public class HazelcastQueryable implements Queryable {
   private static final Log log = LogFactory.getLog(HazelcastQueryable.class);
   protected final Hazelcast36Service service;

   public HazelcastQueryable(Hazelcast36Service service) {
      this.service = service;
   }

   @Override
   public Query.Builder getBuilder(String mapName, Class<?> clazz) {
      return new HazelcastQueryBuilder(clazz);
   }

   @Override
   public Query.Builder getBuilder(String containerName, String queryString) {
      throw new UnsupportedOperationException("RadarGun doesn't support queryString for Hazelcast");
   }

   @Override
   public Query.Context createContext(String containerName) {
      return new HazelcastQuery.Context(service.getMap(containerName));
   }

   @Override
   public void reindex(String containerName) {
      // noop
   }

   private static class HazelcastQueryBuilder implements Query.Builder {
      private final Class<?> clazz;
      private Predicate predicate;
      private Comparator comparator;
      private int limit = -1;
      private int offset = 0;
      private Query.SelectExpression[] projection;

      private HazelcastQueryBuilder(Class<?> clazz) {
         this.clazz = clazz;
      }

      @Override
      public Query.Builder subquery() {
         return new HazelcastQueryBuilder(clazz);
      }

      private void implicitAnd(Predicate p) {
         if (predicate == null) {
            predicate = p;
         } else {
            predicate = Predicates.and(predicate, p);
         }
      }

      @Override
      public Query.Builder eq(Query.SelectExpression selectExpression, Object value) {
         implicitAnd(Predicates.equal(selectExpression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder lt(Query.SelectExpression selectExpression, Object value) {
         implicitAnd(Predicates.lessThan(selectExpression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder le(Query.SelectExpression selectExpression, Object value) {
         implicitAnd(Predicates.lessEqual(selectExpression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder gt(Query.SelectExpression selectExpression, Object value) {
         implicitAnd(Predicates.greaterThan(selectExpression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder ge(Query.SelectExpression selectExpression, Object value) {
         implicitAnd(Predicates.greaterEqual(selectExpression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder between(Query.SelectExpression selectExpression, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive) {
         if (!lowerInclusive || !upperInclusive)
            throw new IllegalArgumentException("Hazelcast supports only inclusive bounds");
         implicitAnd(Predicates.between(selectExpression.attribute, (Comparable) lowerBound, (Comparable) upperBound));
         return this;
      }

      @Override
      public Query.Builder isNull(Query.SelectExpression selectExpression) {
         implicitAnd(Predicates.equal(selectExpression.attribute, null));
         return this;
      }

      @Override
      public Query.Builder like(Query.SelectExpression selectExpression, String pattern) {
         implicitAnd(Predicates.like(selectExpression.attribute, pattern));
         return this;
      }

      @Override
      public Query.Builder contains(Query.SelectExpression selectExpression, Object value) {
         throw new UnsupportedOperationException();
      }

      @Override
      public Query.Builder not(Query.Builder subquery) {
         implicitAnd(Predicates.not(((HazelcastQueryBuilder) subquery).predicate));
         return this;
      }

      @Override
      public Query.Builder any(Query.Builder... subqueries) {
         Predicate p = null;
         for (Query.Builder subquery : subqueries) {
            if (p == null) {
               p = ((HazelcastQueryBuilder) subquery).predicate;
            } else {
               p = Predicates.or(p, ((HazelcastQueryBuilder) subquery).predicate);
            }
         }
         implicitAnd(p);
         return this;
      }

      @Override
      public Query.Builder orderBy(Query.SelectExpression selectExpression) {
         if (!selectExpression.asc) {
            comparator = new HazelcastQuery.InverseComparator(selectExpression.attribute);
         } else {
            comparator = new HazelcastQuery.RegularComparator(selectExpression.attribute);
         }
         return this;
      }

      @Override
      public Query.Builder projection(Query.SelectExpression... selectExpressions) {
         log.warn("Projection is emulated; no native support for projection.");
         for (Query.SelectExpression selectExpression : selectExpressions) {
            if (selectExpression.function != Query.AggregationFunction.NONE && selectExpressions.length != 1)
               throw new RuntimeException("Hazelcast only supports a single aggregation per query!");
         }
         this.projection = selectExpressions;
         return this;
      }

      @Override
      public Query.Builder groupBy(String[] attribute) {
         throw new UnsupportedOperationException();
      }

      @Override
      public Query.Builder offset(long offset) {
         log.warn("Offset is emulated; first records will be loaded anyway.");
         this.offset = (int) offset;
         return this;
      }

      @Override
      public Query.Builder limit(long limit) {
         this.limit = (int) limit;
         return this;
      }

      @Override
      public Query build() {
         if (projection != null && projection.length == 1 && projection[0].function != Query.AggregationFunction.NONE) {
            return new HazelcastAggregationQuery(clazz, predicate, projection[0]);
         }
         Predicate finalPredicate;
         if (comparator == null) {
            if (limit < 0) finalPredicate = predicate;
            else finalPredicate = new PagingPredicate(predicate, limit);
         } else {
            if (limit < 0) finalPredicate = new PagingPredicate(predicate, comparator, Integer.MAX_VALUE);
            else finalPredicate = new PagingPredicate(predicate, comparator, limit);
         }
         String[] stringProjection = new String[projection.length];
         for (int i = 0; i < projection.length; i++) {
            stringProjection[i] = projection[i].attribute;
         }
         return new HazelcastQuery(finalPredicate, offset, stringProjection);
      }
   }


}
