package org.radargun.service;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;
import com.tangosol.net.NamedCache;
import com.tangosol.util.Filter;
import com.tangosol.util.InvocableMap;
import com.tangosol.util.ValueExtractor;
import com.tangosol.util.aggregator.ReducerAggregator;
import com.tangosol.util.comparator.ChainedComparator;
import com.tangosol.util.comparator.EntryComparator;
import com.tangosol.util.comparator.InverseComparator;
import com.tangosol.util.extractor.ChainedExtractor;
import com.tangosol.util.extractor.MultiExtractor;
import com.tangosol.util.extractor.ReflectionExtractor;
import com.tangosol.util.filter.AllFilter;
import com.tangosol.util.filter.AnyFilter;
import com.tangosol.util.filter.BetweenFilter;
import com.tangosol.util.filter.ContainsFilter;
import com.tangosol.util.filter.EqualsFilter;
import com.tangosol.util.filter.GreaterEqualsFilter;
import com.tangosol.util.filter.GreaterFilter;
import com.tangosol.util.filter.IsNullFilter;
import com.tangosol.util.filter.LessEqualsFilter;
import com.tangosol.util.filter.LessFilter;
import com.tangosol.util.filter.LikeFilter;
import com.tangosol.util.filter.LimitFilter;
import com.tangosol.util.filter.NotFilter;

import org.radargun.aggregators.LimitAggregator;
import org.radargun.traits.Query;
import org.radargun.traits.Query.Builder;
import org.radargun.traits.Queryable;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class CoherenceQueryable implements Queryable {
   protected final Coherence3Service service;

   public CoherenceQueryable(Coherence3Service service) {
      this.service = service;
   }

   @Override
   public Query.Builder getBuilder(String containerName, Class<?> clazz) {
      return new QueryBuilderImpl();
   }

   @Override
   public QueryContextImpl createContext(String containerName) {
      return new QueryContextImpl(service.getCache(containerName));
   }

   @Override
   public void reindex(String containerName) {
      // noop - indices should be in sync
   }

   public void registerIndices(NamedCache cache, List<Coherence3Service.IndexedColumn> indexedColumns) {
      for (Coherence3Service.IndexedColumn c : service.indexedColumns) {
         if (cache.getCacheName().equals(c.cache)) {
            ReflectionExtractor extractor = new ReflectionExtractor(c.attribute);
            cache.addIndex(extractor, c.ordered, null);
         }
      }
   }

   private static class QueryBuilderImpl implements Query.Builder {
      private final ArrayList<Filter> filters = new ArrayList<Filter>();
      private LinkedHashMap<String, Boolean> orderBy;
      private long offset = 0, limit = -1;
      private String[] projection;

      @Override
      public Query.Builder subquery() {
         return new QueryBuilderImpl();
      }

      @Override
      public Query.Builder eq(Query.SelectExpression expression, Object value) {
         filters.add(new EqualsFilter(expression.attribute, value));
         return this;
      }

      @Override
      public Query.Builder lt(Query.SelectExpression expression, Object value) {
         filters.add(new LessFilter(expression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder le(Query.SelectExpression expression, Object value) {
         filters.add(new LessEqualsFilter(expression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder gt(Query.SelectExpression expression, Object value) {
         filters.add(new GreaterFilter(expression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder ge(Query.SelectExpression expression, Object value) {
         filters.add(new GreaterEqualsFilter(expression.attribute, (Comparable) value));
         return this;
      }

      @Override
      public Query.Builder between(Query.SelectExpression expression, Object lowerBound, boolean lowerInclusive, Object upperBound,
            boolean upperInclusive) {
         filters.add(new BetweenFilter(expression.attribute, (Comparable) lowerBound, (Comparable) upperBound));
         return this;
      }

      @Override
      public Query.Builder isNull(Query.SelectExpression expression) {
         filters.add(new IsNullFilter(expression.attribute));
         return this;
      }

      @Override
      public Query.Builder like(Query.SelectExpression expression, String pattern) {
         filters.add(new LikeFilter(expression.attribute, pattern));
         return this;
      }

      @Override
      public Query.Builder contains(Query.SelectExpression expression, Object value) {
         filters.add(new ContainsFilter(expression.attribute, value));
         return this;
      }

      @Override
      public Query.Builder not(Query.Builder subquery) {
         filters.add(new NotFilter(((QueryBuilderImpl) subquery).getFilter()));
         return this;
      }

      @Override
      public Query.Builder any(Query.Builder... subqueries) {
         Filter[] subs = new Filter[subqueries.length];
         for (int i = 0; i < subqueries.length; ++i) {
            subs[i] = ((QueryBuilderImpl) subqueries[i]).getFilter();
         }
         filters.add(new AnyFilter(subs));
         return this;
      }

      @Override
      public Query.Builder orderBy(Query.SelectExpression expression) {
         if (orderBy == null)
            orderBy = new LinkedHashMap<String, Boolean>();
         if (orderBy.put(expression.attribute, expression.asc) != null) {
            throw new IllegalArgumentException("Order by " + expression.attribute + " already set");
         }
         return this;
      }

      @Override
      public Query.Builder projection(Query.SelectExpression... expression) {
         if (expression == null || expression.length == 0)
            throw new IllegalArgumentException();
         if (projection != null)
            throw new IllegalStateException("Projection already set");
         this.projection = Arrays.stream(expression).map(Query.SelectExpression::attribute).toArray(String[]::new);
         return this;
      }

      @Override
      public Query.Builder offset(long offset) {
         if (this.offset < 0)
            throw new IllegalArgumentException("Offset " + offset + " < 0 not allowed");
         this.offset = offset;
         return this;
      }

      @Override
      public Query.Builder limit(long limit) {
         if (limit < 1)
            throw new IllegalArgumentException("Limit " + limit + " < 1 not allowed");
         this.limit = limit;
         return this;
      }

      @Override
      public Query build() {
         return new QueryImpl(getFilter(), projection, orderBy, offset, limit);
      }

      public Filter getFilter() {
         if (filters.size() == 1) {
            return filters.get(0);
         } else {
            return new AllFilter(filters.toArray(new Filter[filters.size()]));
         }
      }

      public Builder groupBy(String[] attribute) {
         throw new UnsupportedOperationException();
         // TODO Needs implementing
      }
   }

   private static class QueryImpl implements Query {
      private final Filter filter;
      private final Comparator comparator;
      private final int skip;
      private final int limit;
      private final String[] projection;

      public QueryImpl(Filter filter, String[] projection, LinkedHashMap<String, Boolean> orderBy, long offset, long limit) {
         this.projection = projection;
         if (projection != null) {
            // we delay this to LimitAggregator
            this.skip = (int) offset;
            this.limit = (int) limit;
            this.filter = filter;
            if (orderBy == null) {
               comparator = null;
            } else {
               // TODO: we can project composite object and sort by its inner
               // attributes
               ArrayList<Comparator> comparators = new ArrayList<Comparator>(orderBy.size());
               for (String attribute : orderBy.keySet()) {
                  int index = -1;
                  for (int i = 0; i < projection.length; ++i) {
                     if (projection[i].equals(attribute)) {
                        index = i;
                        break;
                     }
                  }
                  if (index < 0) {
                     throw new IllegalStateException("Attribute " + attribute + " has to be projected to perform sort");
                  } else {
                     comparators.add(new ProjectionComparator(index, orderBy.get(attribute)));
                  }
               }
               if (comparators.size() == 1) {
                  comparator = comparators.get(0);
               } else {
                  comparator = new ChainedComparator(comparators.toArray(new Comparator[comparators.size()]));
               }
            }
         } else {
            if (orderBy == null) {
               comparator = null;
            } else if (orderBy.size() == 1) {
               Map.Entry<String, Boolean> entry = orderBy.entrySet().iterator().next();
               Comparator comp = new ReflectionExtractor(entry.getKey());
               comparator = entry.getValue() ? comp : new InverseComparator(comp);
            } else {
               ArrayList<Comparator> comparators = new ArrayList<Comparator>(orderBy.size());
               for (Map.Entry<String, Boolean> entry : orderBy.entrySet()) {
                  Comparator comp = new ReflectionExtractor(entry.getKey());
                  comparators.add(entry.getValue() ? comp : new InverseComparator(comp));
               }
               comparator = new ChainedComparator(comparators.toArray(new Comparator[comparators.size()]));
            }
            if (offset > 0 && limit >= 0) {
               long bestSize = -1;
               int bestExtra = Integer.MAX_VALUE;
               for (long pageSize = limit; pageSize < 2 * limit || bestSize < 0; ++pageSize) {
                  // if the pageSize does not cover our range properly, we
                  // cannot use it
                  if (pageSize * (offset / pageSize + 1) < offset + limit)
                     continue;
                  int extra = (int) (pageSize - limit);
                  if (extra < bestExtra) {
                     bestExtra = extra;
                     bestSize = pageSize;
                  }
                  if (extra == 0)
                     break;
               }
               this.skip = (int) (offset % bestSize);
               this.limit = (int) limit;
               LimitFilter lf = new LimitFilter(filter, (int) bestSize);
               lf.setPage((int) (offset / bestSize));
               this.filter = lf;
            } else if (offset > 0) {
               this.skip = (int) offset;
               this.limit = Integer.MAX_VALUE;
               this.filter = filter;
            } else if (limit >= 0) {
               this.skip = 0;
               this.limit = (int) limit;
               this.filter = new LimitFilter(filter, this.limit);
            } else {
               this.skip = 0;
               this.limit = -1;
               this.filter = filter;
            }
         }
      }

      @Override
      public Query.Result execute(Query.Context resource) {
         NamedCache cache = getCache(resource);
         if (projection == null) {
            if (comparator == null) {
               return new QueryResultImpl(cache.entrySet(filter), skip, limit);
            } else {
               return new QueryResultImpl(cache.entrySet(filter, comparator), skip, limit);
            }
         } else {
            InvocableMap.EntryAggregator aggregator;
            if (projection.length == 1) {
               aggregator = new ReducerAggregator(projection[0]);
            } else {
               ValueExtractor[] extractors = new ValueExtractor[projection.length];
               for (int i = 0; i < projection.length; ++i) {
                  extractors[i] = projection[i].indexOf('.') < 0 ? new ReflectionExtractor(projection[i])
                        : new ChainedExtractor(projection[i]);
               }
               aggregator = new ReducerAggregator(new MultiExtractor(extractors));
            }
            Map map;
            if (comparator != null || skip > 0 || limit >= 0) {
               int laLimit = limit < 0 ? Integer.MAX_VALUE : limit + skip;
               map = (Map) cache.aggregate(filter,
                     new LimitAggregator(aggregator, laLimit, comparator != null, comparator, EntryComparator.CMP_VALUE));
            } else {
               map = (Map) cache.aggregate(filter, aggregator);
            }
            return new QueryResultImpl(map.values(), skip, limit);
         }
      }

      protected NamedCache getCache(Query.Context context) {
         QueryContextImpl impl = (QueryContextImpl) context;
         return impl.cache;
      }
   }

   private static class QueryResultImpl implements Query.Result {
      private final Set<Map.Entry> entrySet;
      private final Collection valueSet;
      private final int skip;
      private final int limit;

      public QueryResultImpl(Set<Map.Entry> entrySet, int skip, int limit) {
         this.entrySet = entrySet;
         this.valueSet = null;
         this.skip = skip;
         this.limit = limit;
      }

      public QueryResultImpl(Collection valueSet, int skip, int limit) {
         this.entrySet = null;
         this.valueSet = valueSet;
         this.skip = skip;
         this.limit = limit;
      }

      @Override
      public int size() {
         return Math.min(Math.max(0, (entrySet != null ? entrySet.size() : valueSet.size()) - skip), limit());
      }

      @Override
      public Collection values() {
         if (entrySet != null) {
            return entrySet.stream().skip(skip).limit(limit()).map(e -> e.getValue()).collect(Collectors.toCollection(HashSet::new));
         } else {
            return (Collection) valueSet.stream().skip(skip).limit(limit()).map(e -> (e instanceof List ? ((List) e).toArray() : e))
                  .collect(Collectors.toCollection(HashSet::new));
         }
      }

      private int limit() {
         return limit < 0 ? Integer.MAX_VALUE : limit;
      }
   }

   protected static class QueryContextImpl implements Query.Context {
      protected final NamedCache cache;

      public QueryContextImpl(NamedCache cache) {
         this.cache = cache;
      }

      @Override
      public void close() {
      }
   }

   public static class ProjectionComparator implements Comparator, Serializable, PortableObject {
      private int index;
      private boolean asc;

      public ProjectionComparator() {
         // for POF deserialization only
      }

      private ProjectionComparator(int index, boolean asc) {
         this.index = index;
         this.asc = asc;
      }

      @Override
      public int compare(Object o1, Object o2) {
         return asc ? compareAsc(o1, o2) : compareAsc(o2, o1);
      }

      private final int compareAsc(Object o1, Object o2) {
         Object attr1 = o1 instanceof List ? ((List) o1).get(index) : o1;
         Object attr2 = o2 instanceof List ? ((List) o2).get(index) : o2;
         if (attr1 == null) {
            return attr2 == null ? 0 : -1;
         } else if (attr2 == null) {
            return 1;
         } else {
            return ((Comparable) attr1).compareTo(attr2);
         }
      }

      @Override
      public void readExternal(PofReader pofReader) throws IOException {
         index = pofReader.readInt(0);
         asc = pofReader.readBoolean(1);
      }

      @Override
      public void writeExternal(PofWriter pofWriter) throws IOException {
         pofWriter.writeInt(0, index);
         pofWriter.writeBoolean(1, asc);
      }
   }
}
