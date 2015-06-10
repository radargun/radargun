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
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HazelcastQueryable implements Queryable {
   private static final Log log = LogFactory.getLog(HazelcastQueryable.class);
   protected final Hazelcast3Service service;

   public HazelcastQueryable(Hazelcast3Service service) {
      this.service = service;
   }

   @Override
   public Query.Builder getBuilder(String mapName, Class<?> clazz) {
      return new HazelcastQueryBuilder();
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
      private Predicate predicate;
      private Comparator comparator;
      private int limit = -1;
      private int offset = 0;
      private String[] projection;

      private HazelcastQueryBuilder() {
      }

      @Override
      public Query.Builder subquery() {
         return new HazelcastQueryBuilder();
      }

      private void implicitAnd(Predicate p) {
         if (predicate == null) {
            predicate = p;
         } else {
            predicate = Predicates.and(predicate, p);
         }
      }

      @Override
      public Query.Builder eq(String attribute, Object value) {
         implicitAnd(Predicates.equal(attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder lt(String attribute, Object value) {
         implicitAnd(Predicates.lessThan(attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder le(String attribute, Object value) {
         implicitAnd(Predicates.lessEqual(attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder gt(String attribute, Object value) {
         implicitAnd(Predicates.greaterThan(attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder ge(String attribute, Object value) {
         implicitAnd(Predicates.greaterEqual(attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder between(String attribute, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive) {
         if (!lowerInclusive || !upperInclusive) throw new IllegalArgumentException("Hazelcast supports only inclusive bounds");
         implicitAnd(Predicates.between(attribute, (Comparable) lowerBound, (Comparable) upperBound));
         return this;
      }

      @Override
      public Query.Builder isNull(String attribute) {
         implicitAnd(Predicates.equal(attribute, null));
         return this;
      }

      @Override
      public Query.Builder like(String attribute, String pattern) {
         implicitAnd(Predicates.like(attribute, pattern));
         return this;
      }

      @Override
      public Query.Builder contains(String attribute, Object value) {
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
      public Query.Builder orderBy(String attribute, Query.SortOrder order) {
         if (order == Query.SortOrder.DESCENDING) {
            comparator = new HazelcastQuery.InverseComparator(attribute);
         } else {
            comparator = new HazelcastQuery.RegularComparator(attribute);
         }
         return this;
      }

      @Override
      public Query.Builder projection(String... attributes) {
         log.warn("Projection is emulated; no native support for projection.");
         this.projection = attributes;
         return this;
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
         Predicate finalPredicate;
         if (comparator == null) {
            if (limit < 0) finalPredicate = predicate;
            else finalPredicate = new PagingPredicate(predicate, limit);
         } else {
            if (limit < 0) finalPredicate = new PagingPredicate(predicate, comparator, Integer.MAX_VALUE);
            else finalPredicate = new PagingPredicate(predicate, comparator, limit);
         }
         return new HazelcastQuery(finalPredicate, offset, projection);
      }
   }


}
