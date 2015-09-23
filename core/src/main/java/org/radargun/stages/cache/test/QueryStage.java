package org.radargun.stages.cache.test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Converter;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.Stressor;
import org.radargun.stages.test.TestStage;
import org.radargun.state.SlaveState;
import org.radargun.stats.Statistics;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Queryable;
import org.radargun.utils.NumberConverter;
import org.radargun.utils.ObjectConverter;
import org.radargun.utils.Projections;
import org.radargun.utils.ReflexiveConverters;

/**
 * Executes Queries using Infinispan-Query API against the cache.
 *
 * The conditions field are the standard conditions that you would expect in a WHERE clause.
 * Having refers to the conditions which follow the GROUP BY clause, and can make use
 * of aggregations.
 *
 * @author Anna Manukyan
 */
@Stage(doc = "Stage which executes a Query using Infinispan-query API against all keys in the cache.")
public class QueryStage extends TestStage {
   @Property(optional = false, doc = "Full class name of the object that should be queried. Mandatory.")
   private String queryObjectClass;

   @Property(optional = false, doc = "Conditions used in the query", complexConverter = ConditionConverter.class)
   private List<Condition> conditions;

   @Property(doc = "Having conditions used in the query", complexConverter = ConditionConverter.class)
   private List<Condition> having;

   @Property(doc = "Use projection with or without aggregation instead of returning full object, in form " +
         "[count|sum|avg|min|max](attribute1),attribute2, etc. Default is without projection.",
         converter = ProjectionConverter.class)
   private List<Queryable.Attribute> projection;

   @Property(doc = "Use sorting order, in form [attribute[:(ASC|DESC)]][,attribute[:(ASC|DESC)]]*. " +
         "Without specifying ASC or DESC the sort order defaults to ASC. Default is unordereded.",
         converter = SortConverter.class)
   private List<SortElement> orderBy;

   @Property(doc = "Use grouping, in form [attribute][,attribute]*. Default is without grouping.")
   private String[] groupBy;

   @Property(doc = "Offset in the results. Default is none.")
   private long offset = -1;

   @Property(doc = "Maximum number of the results. Default is none.")
   private long limit = -1;

   @Property(doc = "Check whether all slaves got the same result, and fail if not. Default is false.")
   private boolean checkSameResult = false;

   @InjectTrait
   private Queryable queryable;

   protected AtomicInteger expectedSize = new AtomicInteger(-1);

   @Override
   public OperationLogic getLogic() {
      return new Logic();
   }

   @Override
   protected DistStageAck newStatisticsAck(List<Stressor> stressors) {
      return new QueryAck(slaveState, gatherResults(stressors, new StatisticsResultRetriever()), expectedSize.get());
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      int minSize = Integer.MAX_VALUE, maxSize = Integer.MIN_VALUE;
      Map<Integer, Report.SlaveResult> slaveResults = new HashMap<Integer, Report.SlaveResult>();
      for (QueryAck ack : Projections.instancesOf(acks, QueryAck.class)) {
         if (maxSize >= 0 && (minSize != ack.queryResultSize || maxSize != ack.queryResultSize)) {
            String message = String.format("The size got from %d -> %d is not the same as from other slaves -> %d .. %d ",
                  ack.getSlaveIndex(), ack.queryResultSize, minSize, maxSize);
            if (checkSameResult) {
               log.error(message);
               return errorResult();
            } else {
               log.info(message);
            }
         }
         minSize = Math.min(minSize, ack.queryResultSize);
         maxSize = Math.max(maxSize, ack.queryResultSize);
         slaveResults.put(ack.getSlaveIndex(), new Report.SlaveResult(String.valueOf(ack.queryResultSize), false));
      }
      Report.Test test = getTest(true); // the test was already created in super.processAckOnMaster
      if (test != null) {
         String sizeString = minSize == maxSize ? String.valueOf(maxSize) : String.format("%d .. %d", minSize, maxSize);
         test.addResult(getTestIteration(), new Report.TestResult("Query result size", slaveResults, sizeString, false));
      } else {
         log.info("No test name - results are not recorded");
      }
      return result;
   }

   protected static class QueryAck extends StatisticsAck {
      public final int queryResultSize;

      public QueryAck(SlaveState slaveState, List<List<Statistics>> iterations, int queryResultSize) {
         super(slaveState, iterations);
         this.queryResultSize = queryResultSize;
      }
   }

   protected class Logic extends OperationLogic {
      protected Queryable.QueryBuilder builder;
      protected Queryable.QueryResult previousQueryResult = null;

      @Override
      public void init(Stressor stressor) {
         super.init(stressor);
         Class<?> clazz;
         try {
            clazz = Class.forName(queryObjectClass);
         } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot load class " + queryObjectClass, e);
         }
         builder = queryable.getBuilder(null, clazz);
         for (Condition condition : conditions) {
            condition.apply(builder);
         }
         if (orderBy != null) {
            for (SortElement se : orderBy) {
               builder.orderBy(new Queryable.Attribute(se.attribute), se.asc ? Queryable.SortOrder.ASCENDING : Queryable.SortOrder.DESCENDING);
            }
         }
         if (projection != null) {
            builder.projection(projection.toArray(new Queryable.Attribute[projection.size()]));
         }
         if (groupBy != null) {
            builder.groupBy(groupBy);
            if (having != null) {
               for (Condition condition : having) {
                  condition.apply(builder);
               }
            }
         }
         if (offset >= 0) {
            builder.offset(offset);
         }
         if (limit >= 0) {
            builder.limit(limit);
         }
      }

      @Override
      public Object run() throws RequestException {
         Queryable.Query query = builder.build();
         Queryable.QueryResult queryResult = (Queryable.QueryResult) stressor.makeRequest(new Invocations.Query(query));

         if (previousQueryResult != null) {
            if (queryResult.size() != previousQueryResult.size()) {
               throw new IllegalStateException("The query result is different from the previous one. All results should be the same when executing the same query");
            }
         } else {
            log.info("First result has " + queryResult.size() + " entries");
            if (log.isTraceEnabled()) {
               for (Object entry : queryResult.values()) {
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
         return PropertyHelper.getDefinitionElementName(getClass()) + PropertyHelper.toString(this);
      }
   }

   private static abstract class PathCondition extends Condition {
      @Property(doc = "Target path (field on the queried object or path through embedded objects)", optional = false, converter = AttributeConverter.class)
      public Queryable.Attribute path;
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

   @DefinitionElement(name = "between", doc = "Target is between two values")
   private static class Between extends PathCondition {
      @Property(doc = "Lower bound for the value", optional = false, converter = NumberConverter.class)
      public Number lowerBound;

      @Property(doc = "Does the range include the lower-bound? Default is true.")
      public boolean lowerInclusive = true;

      @Property(doc = "Upper bound for the value", optional = false, converter = NumberConverter.class)
      public Number upperBound;

      @Property(doc = "Does the range include the upper-bound? Default is true.")
      public boolean upperInclusive = true;

      @Override
      public void apply(Queryable.QueryBuilder builder) {
         builder.between(path, lowerBound, lowerInclusive, upperBound, upperInclusive);
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

   @DefinitionElement(name = "all", doc = "All inner conditions are true", resolveType = DefinitionElement.ResolveType.PASS_BY_DEFINITION)
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

   private static class ConditionConverter extends ReflexiveConverters.ListConverter {
      public ConditionConverter() {
         super(new Class[] {Eq.class, Lt.class, Le.class, Gt.class, Ge.class, Between.class, Like.class, Contains.class, IsNull.class, Not.class, Any.class, All.class});
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
         if (value == null) return "<unordered>";
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

   private static class ProjectionConverter implements Converter<List<Queryable.Attribute>> {
      @Override
      public List<Queryable.Attribute> convert(String string, Type type) {
         String[] parts = string.split(",", 0);
         ArrayList<Queryable.Attribute> result = new ArrayList<>(parts.length);
         for (String part : parts) {
            int leftBrace = part.indexOf('(');
            int rightBrace = part.indexOf(')');
            if (leftBrace < 0) {
               result.add(new Queryable.Attribute(part.trim()));
            } else {
               String fnc = part.substring(0, leftBrace).trim();
               Queryable.AggregationFunction function = Queryable.AggregationFunction.parseFunction(fnc);
               result.add(new Queryable.Attribute(part.substring(leftBrace + 1, rightBrace).trim(), function));
            }
         }
         return result;
      }

      @Override
      public String convertToString(List<Queryable.Attribute> value) {
         if (value == null) return "<no projections>";
         StringBuilder sb = new StringBuilder();
         for (Queryable.Attribute a : value) {
            if (a.function != Queryable.AggregationFunction.NONE) {
               sb.append(a.function).append('(').append(a.attribute).append("), ");
            } else {
               sb.append(a.attribute).append(", ");
            }

         }
         return sb.toString();
      }

      @Override
      public String allowedPattern(Type type) {
         // (AGG_FUNCTION(ATR) | ATR)? (,\\s* AGG_FUNCTION(ATR) | ATR)*
         return "(((COUNT|SUM|AVG|MIN|MAX)\\([0-9a-zA-Z_]*\\))|[0-9a-zA-Z_]*)?(,\\s*(((COUNT|SUM|AVG|MIN|MAX)\\([0-9a-zA-Z_]*\\))|[0-9a-zA-Z_]*))*";
      }
   }

   private static class AttributeConverter implements Converter<Queryable.Attribute> {
      @Override
      public Queryable.Attribute convert(String string, Type type) {
         int leftBrace = string.indexOf('(');
         int rightBrace = string.indexOf(')');
         if (leftBrace < 0) {
            return new Queryable.Attribute(string.trim());
         } else {
            String fnc = string.substring(0, leftBrace).trim();
            Queryable.AggregationFunction function = Queryable.AggregationFunction.parseFunction(fnc);
            return new Queryable.Attribute(string.substring(leftBrace + 1, rightBrace).trim(), function);
         }
      }

      @Override
      public String convertToString(Queryable.Attribute value) {
         if (value == null) return "<no attribute>";
         if (value.function != Queryable.AggregationFunction.NONE) {
            return value.function + "(" + value.attribute + ")";
         } else {
            return value.attribute;
         }
      }

      @Override
      public String allowedPattern(Type type) {
         // (AGG_FUNCTION(ATR) | ATR)
         return "(((COUNT|SUM|AVG|MIN|MAX)\\([0-9a-zA-Z_]*\\))|[0-9a-zA-Z_]*)";
      }
   }
}
