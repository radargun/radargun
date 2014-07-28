package org.radargun.stages.cache.stresstest;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.radargun.DistStageAck;
import org.radargun.config.Converter;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;
import org.radargun.state.SlaveState;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Queryable;
import org.radargun.utils.NumberConverter;
import org.radargun.utils.ObjectConverter;
import org.radargun.utils.ReflexiveListConverter;

/**
 * Executes Queries using Infinispan-Query API against the cache.
 *
 * @author Anna Manukyan
 */
@Stage(doc = "Stage which executes a Query using Infinispan-query API against all keys in the cache.")
public class QueryStage extends StressTestStage {

   @Property(optional = false, doc = "Full class name of the object that should be queried. Mandatory.")
   private String queryObjectClass;

   @Property(optional = false, doc = "Conditions used in the query", complexConverter = ConditionConverter.class)
   private List<Condition> conditions;

   @Property(doc = "Use projection instead of returning full object. Default is without projection.")
   private String[] projection;

   @Property(doc = "Use sorting order, in form [attribute[:(ASC|DESC)]][,attribute[:(ASC|DESC)]]*. " +
         "Without specifying ASC or DESC the sort order defaults to ASC. Default is unordereded.",
         converter = SortConverter.class)
   private List<SortElement> orderBy;

   @Property(doc = "Offset in the results. Default is none.")
   private long offset = -1;

   @Property(doc = "Maximum number of the results. Default is none.")
   private long limit = -1;

   @InjectTrait
   private Queryable queryable;

   protected AtomicInteger expectedSize = new AtomicInteger(-1);

   @Override
   protected Stressor createStressor(int threadIndex) {
      Stressor stressor = super.createStressor(threadIndex);
      stressor.setQueryable(queryable);
      return stressor;
   }

   @Override
   public OperationLogic getLogic() {
      return new QueryRunnerLogic();
   }

   @Override
   public DistStageAck executeOnSlave() {
      return new QueryAck(slaveState, super.executeOnSlave(), expectedSize.get());
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      ArrayList<DistStageAck> unwrapped = new ArrayList<DistStageAck>(acks.size());
      int size = -1;
      for (DistStageAck ack : acks) {
         if (ack instanceof QueryAck) {
            unwrapped.add(((QueryAck) ack).wrapped);
            int ackSize = ((QueryAck) ack).queryResultSize;
            if (size < 0) {
               size = ackSize;
            } else if (size != ((QueryAck) ack).queryResultSize) {
               log.error("The size got from " + ack.getSlaveIndex() + " = " + ackSize + " is not the same as from other slaves = " + size);
               return false;
            }
         } else {
            unwrapped.add(ack);
         }
      }
      return super.processAckOnMaster(unwrapped);
   }

   protected static class QueryAck extends DistStageAck {
      DistStageAck wrapped;
      int queryResultSize;

      public QueryAck(SlaveState slaveState, DistStageAck wrapped, int queryResultSize) {
         super(slaveState);
         this.wrapped = wrapped;
         this.queryResultSize = queryResultSize;
      }
   }

   protected class QueryRunnerLogic implements OperationLogic {
      protected Queryable.QueryBuilder builder;
      protected Queryable.QueryResult previousQueryResult = null;

      @Override
      public void init(Stressor stressor) {
         Class<?> clazz;
         try {
            clazz = slaveState.getClassLoadHelper().getLoader().loadClass(queryObjectClass);
         } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot load class " + queryObjectClass, e);
         }
         builder = queryable.getBuilder(null, clazz);
         for (Condition condition : conditions) {
            condition.apply(builder);
         }
         if (orderBy != null) {
            for (SortElement se : orderBy) {
               builder.orderBy(se.attribute, se.asc ? Queryable.SortOrder.ASCENDING : Queryable.SortOrder.DESCENDING);
            }
         }
         if (projection != null) {
            builder.projection(projection);
         }
         if (offset >= 0) {
            builder.offset(offset);
         }
         if (limit >= 0) {
            builder.limit(limit);
         }
      }

      @Override
      public Object run(Stressor stressor) throws RequestException {
         Queryable.Query query = builder.build();
         Queryable.QueryResult queryResult = (Queryable.QueryResult) stressor.makeRequest(Queryable.QUERY, query);

         if (previousQueryResult != null) {
            if (queryResult.size() != previousQueryResult.size()) {
               throw new IllegalStateException("The query result is different from the previous one. All results should be the same when executing the same query");
            }
         } else {
            log.info("First result has " + queryResult.size() + " entries");
            if (log.isTraceEnabled()) {
               for (Object entry : queryResult.list()) {
                  log.trace(String.valueOf(entry));
               }
            }
            if (!expectedSize.compareAndSet(-1, queryResult.size())) {
               if (expectedSize.get() != queryResult.size()) {
                  throw new IllegalStateException("Another thread reported " + expectedSize.get() + " results while we have " + queryResult.size());
               }
            }
         }
         previousQueryResult = queryResult;

         return queryResult;
      }
   }

   private static abstract class Condition {
      public abstract void apply(Queryable.QueryBuilder builder);

      public String toString() {
         DefinitionElement de = getClass().getAnnotation(DefinitionElement.class);
         StringBuilder sb = new StringBuilder();
         if (de == null) sb.append(getClass().getSimpleName());
         else sb.append(de.name());
         return sb.append(PropertyHelper.toString(this)).toString();
      }
   }

   private static abstract class PathCondition extends Condition {
      @Property(doc = "Target path (field on the queried object or path through embedded objects)", optional = false)
      public String path;
   }

   @DefinitionElement(name = "eq", doc = "Target is equal to value")
   private static class Eq extends PathCondition {
      @Property(doc = "Value used in the condition", optional = false, converter = ObjectConverter.class)
      public Object value;

      @Override
      public void apply(Queryable.QueryBuilder builder) {
         builder.eq(path, value);
      }
   }

   private static abstract class PathNumberCondition extends PathCondition {
      @Property(doc = "Value used in the condition", optional = false, converter = NumberConverter.class)
      public Number value;
   }


   @DefinitionElement(name = "lt", doc = "Target is < than value")
   private static class Lt extends PathNumberCondition {
      @Override
      public void apply(Queryable.QueryBuilder builder) {
         builder.lt(path, value);
      }
   }

   @DefinitionElement(name = "le", doc = "Target is <= than value")
   private static class Le extends PathNumberCondition {
      @Override
      public void apply(Queryable.QueryBuilder builder) {
         builder.le(path, value);
      }
   }

   @DefinitionElement(name = "gt", doc = "Target is > than value")
   private static class Gt extends PathNumberCondition {
      @Override
      public void apply(Queryable.QueryBuilder builder) {
         builder.gt(path, value);
      }
   }

   @DefinitionElement(name = "ge", doc = "Target is < than value")
   private static class Ge extends PathNumberCondition {
      @Override
      public void apply(Queryable.QueryBuilder builder) {
         builder.ge(path, value);
      }
   }

   @DefinitionElement(name = "like", doc = "Target string matches the value")
   private static class Like extends PathCondition {
      @Property(doc = "Value used in the condition", optional = false)
      public String value;

      @Override
      public void apply(Queryable.QueryBuilder builder) {
         builder.like(path, value);
      }
   }

   @DefinitionElement(name = "contains", doc = "Target is collection containing the value")
   private static class Contains extends PathCondition {
      @Property(doc = "Value used in the condition", optional = false, converter = ObjectConverter.class)
      public Object value;

      @Override
      public void apply(Queryable.QueryBuilder builder) {
         builder.contains(path, value);
      }
   }

   @DefinitionElement(name = "is-null", doc = "Target is not defined (null)")
   private static class IsNull extends PathCondition {
      @Override
      public void apply(Queryable.QueryBuilder builder) {
         builder.isNull(path);
      }
   }

   @DefinitionElement(name = "not", doc = "All inner conditions are false", resolveType = DefinitionElement.ResolveType.PASS_BY_DEFINITION)
   private static class Not extends Condition {
      @Property(name = "", doc = "Inner conditions", complexConverter = ConditionConverter.class)
      public final List<Condition> subs = new ArrayList<Condition>();

      @Override
      public void apply(Queryable.QueryBuilder builder) {
         Queryable.QueryBuilder subBuilder = builder.subquery();
         for (Condition sub : subs) {
            sub.apply(subBuilder);
         }
         builder.not(subBuilder);
      }
   }

   @DefinitionElement(name = "any", doc = "Any of inner conditions is true", resolveType = DefinitionElement.ResolveType.PASS_BY_DEFINITION)
   private static class Any extends Condition {
      @Property(name = "", doc = "Inner conditions", complexConverter = ConditionConverter.class)
      public final List<Condition> subs = new ArrayList<Condition>();

      @Override
      public void apply(Queryable.QueryBuilder builder) {
         Queryable.QueryBuilder subBuilders[] = new Queryable.QueryBuilder[subs.size()];
         int i = 0;
         for (Condition sub : subs) {
            Queryable.QueryBuilder subBuilder = builder.subquery();
            sub.apply(subBuilder);
            subBuilders[i++] = subBuilder;
         }
         builder.any(subBuilders);
      }
   }

   @DefinitionElement(name = "all", doc = "All inner conditions are false", resolveType = DefinitionElement.ResolveType.PASS_BY_DEFINITION)
   private static class All extends Condition {
      @Property(name = "", doc = "Inner conditions", complexConverter = ConditionConverter.class)
      public final List<Condition> subs = new ArrayList<Condition>();

      @Override
      public void apply(Queryable.QueryBuilder builder) {
         for (Condition sub : subs) {
            sub.apply(builder);
         }
      }
   }

   private static class ConditionConverter extends ReflexiveListConverter {
      public ConditionConverter() {
         super(new Class[] {Eq.class, Lt.class, Le.class, Gt.class, Ge.class, Like.class, Contains.class, IsNull.class, Not.class, Any.class, All.class});
      }
   }

   private static class ObjectsConverter extends ReflexiveListConverter {
      public ObjectsConverter() {
         super(new Class[] { /* TODO */ });
      }
   }

   private static class SortElement {
      public final String attribute;
      public final boolean asc;

      private SortElement(String attribute, boolean asc) {
         this.attribute = attribute;
         this.asc = asc;
      }
   }

   private static class SortConverter implements Converter<List<SortElement>> {
      @Override
      public List<SortElement> convert(String string, Type type) {
         String[] parts = string.split(",", 0);
         ArrayList<SortElement> result = new ArrayList<SortElement>(parts.length);
         for (String part : parts) {
            int colon = part.indexOf(':');
            if (colon < 0) {
               result.add(new SortElement(part.trim(), true));
            } else {
               String order = part.substring(colon + 1).trim();
               boolean asc;
               if (order.equalsIgnoreCase("ASC")) {
                  asc = true;
               } else if (order.equalsIgnoreCase("DESC")) {
                  asc = false;
               } else {
                  throw new IllegalArgumentException("Sort order: " + order);
               }
               result.add(new SortElement(part.substring(0, colon).trim(), asc));
            }
         }
         return result;
      }

      @Override
      public String convertToString(List<SortElement> value) {
         StringBuilder sb = new StringBuilder();
         for (SortElement e : value) {
            sb.append(e.attribute).append(':').append(e.asc ? "ASC" : "DESC").append(", ");
         }
         return sb.toString();
      }

      @Override
      public String allowedPattern(Type type) {
         return "[0-9a-zA-Z_]*(:ASC|:DESC)?(,\\s*[0-9a-zA-Z_]*(:ASC|:DESC)?)*";
      }
   }
}
