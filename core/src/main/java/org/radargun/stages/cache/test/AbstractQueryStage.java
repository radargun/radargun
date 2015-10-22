package org.radargun.stages.cache.test;

import org.radargun.config.Converter;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.TestStage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Queryable;
import org.radargun.utils.NumberConverter;
import org.radargun.utils.ObjectConverter;
import org.radargun.utils.ReflexiveConverters;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for stages which contain queries
 *
 * The conditions field are the standard conditions that you would expect in a WHERE clause.
 * Having refers to the conditions which follow the GROUP BY clause, and can make use
 * of aggregations. Projection and orderBy have both alternative versions
 * (projectionAggregated, orderByAggregatedColumns) which can use aggregations, too.
 */
public abstract class AbstractQueryStage extends TestStage {

    @Property(optional = false, doc = "Full class name of the object that should be queried. Mandatory.")
    protected String queryObjectClass;

    @Property(optional = false, doc = "Conditions used in the query.", complexConverter = ConditionConverter.class)
    protected List<Condition> conditions;

    @Property(doc = "Conditions applied to groups when using group-by, can use aggregations.", complexConverter = ConditionConverter.class)
    protected List<Condition> having;

    @Property(doc = "Use projection instead of returning full object. Default is without projection.")
    protected String[] projection;

    @Property(doc = "Projection, possibly with aggregations.",
            complexConverter = ProjectionConverter.class)
    protected List<SelectExpressionElement>  projectionAggregated;

    @Property(doc = "Use sorting order, in form [attribute[:(ASC|DESC)]][,attribute[:(ASC|DESC)]]*. " +
            "Without specifying ASC or DESC the sort order defaults to ASC. Default is unordereded.",
            converter = SortConverter.class)
    protected List<SortElement> orderBy;

    @Property(doc = "Sorting, possibly by aggregated columns.",
          complexConverter = AggregatedSortConverter.class)
    protected List<OrderedSelectExpressionElement> orderByAggregatedColumns;

    @Property(doc = "Use grouping, in form [attribute][,attribute]*. Default is without grouping.")
    protected String[] groupBy;

    @Property(doc = "Offset in the results. Default is none.")
    protected long offset = -1;

    @Property(doc = "Maximum number of the results. Default is none.")
    protected long limit = -1;

    @Property(doc = "Check whether all slaves got the same result, and fail if not. Default is false.")
    protected boolean checkSameResult = false;

    @InjectTrait
    protected Queryable queryable;

    protected AtomicInteger expectedSize = new AtomicInteger(-1);

    public Queryable.QueryBuilder constructBuilder() {
        Class<?> clazz;
        try {
            clazz = slaveState.getClassLoader().loadClass(queryObjectClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot load class " + queryObjectClass, e);
        }
        Queryable.QueryBuilder builder = queryable.getBuilder(null, clazz);
        if (conditions != null) {
            for (Condition condition : conditions) {
                condition.apply(builder);
            }
        }
        if (orderBy != null) {
            for (SortElement se : orderBy) {
                builder.orderBy(new Queryable.SelectExpression(se.attribute), se.asc ? Queryable.SortOrder.ASCENDING : Queryable.SortOrder.DESCENDING);
            }
        } else if (orderByAggregatedColumns != null) {
            for (OrderedSelectExpressionElement orderByAggregatedColumn : orderByAggregatedColumns) {
                builder.orderBy(orderByAggregatedColumn.toSelectExpression(), orderByAggregatedColumn.toSelectExpression().asc ?
                      Queryable.SortOrder.ASCENDING : Queryable.SortOrder.DESCENDING);
            }
        }
        if (projection != null) {
            Queryable.SelectExpression[] projections = new Queryable.SelectExpression[projection.length];
            for (int i = 0; i < projection.length; i++) {
                projections[i] = new Queryable.SelectExpression(projection[i]);
            }
            builder.projection(projections);
        } else if (projectionAggregated != null) {
            Queryable.SelectExpression[] projections = new Queryable.SelectExpression[projectionAggregated.size()];
            for (int i = 0; i < projectionAggregated.size(); i++) {
                projections[i] = projectionAggregated.get(i).toSelectExpression();
            }
            builder.projection(projections);
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
        return builder;
    }

    public abstract OperationLogic getLogic();

    protected static abstract class Condition {
        public abstract void apply(Queryable.QueryBuilder builder);

        public String toString() {
            return PropertyHelper.getDefinitionElementName(getClass()) + PropertyHelper.toString(this);
        }
    }

    protected static abstract class PathCondition extends Condition {
        @Property(doc = "Target path (field on the queried object or path through embedded objects)")
        public String path;

        @Property(doc = "Target path, can be aggregated.", complexConverter = SelectExpressionConverter.class)
        public SelectExpressionElement aggregatedPath;

        Queryable.SelectExpression resolvedPath;

        public void apply(Queryable.QueryBuilder builder) {
            if (path != null) {
                resolvedPath = new Queryable.SelectExpression(path);
            }
            if (aggregatedPath != null) {
                resolvedPath = aggregatedPath.toSelectExpression();
            }
            if (path != null && aggregatedPath != null) {
                throw new IllegalStateException("Condition can only have a single target path!");
            }
            if (resolvedPath == null) {
                throw new IllegalStateException("You need to specify a target path for the condition!");
            }
        }
    }

    @DefinitionElement(name = "eq", doc = "Target is equal to value")
    protected static class Eq extends PathCondition {
        @Property(doc = "Value used in the condition", optional = false, converter = ObjectConverter.class)
        public Object value;

        @Override
        public void apply(Queryable.QueryBuilder builder) {
            super.apply(builder);
            builder.eq(resolvedPath, value);
        }
    }

    protected static abstract class PathNumberCondition extends PathCondition {
        @Property(doc = "Value used in the condition", optional = false, converter = NumberConverter.class)
        public Number value;
    }


    @DefinitionElement(name = "lt", doc = "Target is < than value")
    protected static class Lt extends PathNumberCondition {
        @Override
        public void apply(Queryable.QueryBuilder builder) {
            super.apply(builder);
            builder.lt(resolvedPath, value);
        }
    }

    @DefinitionElement(name = "le", doc = "Target is <= than value")
    protected static class Le extends PathNumberCondition {
        @Override
        public void apply(Queryable.QueryBuilder builder) {
            super.apply(builder);
            builder.le(resolvedPath, value);
        }
    }

    @DefinitionElement(name = "gt", doc = "Target is > than value")
    protected static class Gt extends PathNumberCondition {
        @Override
        public void apply(Queryable.QueryBuilder builder) {
            super.apply(builder);
            builder.gt(resolvedPath, value);
        }
    }

    @DefinitionElement(name = "ge", doc = "Target is < than value")
    protected static class Ge extends PathNumberCondition {
        @Override
        public void apply(Queryable.QueryBuilder builder) {
            super.apply(builder);
            builder.ge(resolvedPath, value);
        }
    }

    @DefinitionElement(name = "between", doc = "Target is between two values")
    protected static class Between extends PathCondition {
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
            super.apply(builder);
            builder.between(resolvedPath, lowerBound, lowerInclusive, upperBound, upperInclusive);
        }
    }

    @DefinitionElement(name = "like", doc = "Target string matches the value")
    protected static class Like extends PathCondition {
        @Property(doc = "Value used in the condition", optional = false)
        public String value;

        @Override
        public void apply(Queryable.QueryBuilder builder) {
            super.apply(builder);
            builder.like(resolvedPath, value);
        }
    }

    @DefinitionElement(name = "contains", doc = "Target is collection containing the value")
    protected static class Contains extends PathCondition {
        @Property(doc = "Value used in the condition", optional = false, converter = ObjectConverter.class)
        public Object value;

        @Override
        public void apply(Queryable.QueryBuilder builder) {
            super.apply(builder);
            builder.contains(resolvedPath, value);
        }
    }

    @DefinitionElement(name = "is-null", doc = "Target is not defined (null)")
    protected static class IsNull extends PathCondition {
        @Override
        public void apply(Queryable.QueryBuilder builder) {
            super.apply(builder);
            builder.isNull(resolvedPath);
        }
    }

    @DefinitionElement(name = "not", doc = "All inner conditions are false", resolveType = DefinitionElement.ResolveType.PASS_BY_DEFINITION)
    protected static class Not extends Condition {
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
    protected static class Any extends Condition {
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
    protected static class All extends Condition {
        @Property(name = "", doc = "Inner conditions", complexConverter = ConditionConverter.class)
        public final List<Condition> subs = new ArrayList<Condition>();

        @Override
        public void apply(Queryable.QueryBuilder builder) {
            for (Condition sub : subs) {
                sub.apply(builder);
            }
        }
    }

    protected static class ConditionConverter extends ReflexiveConverters.ListConverter {
        public ConditionConverter() {
            super(new Class[] {Eq.class, Lt.class, Le.class, Gt.class, Ge.class, Between.class, Like.class, Contains.class, IsNull.class, Not.class, Any.class, All.class});
        }
    }

    protected static abstract class SelectExpressionElement {
        @Property(doc = "Target path (field on the queried object or path through embedded objects)", optional = false)
        public String path;

        public abstract Queryable.SelectExpression toSelectExpression();

        public String toString() {
            return PropertyHelper.getDefinitionElementName(getClass()) + PropertyHelper.toString(this);
        }
    }

    @DefinitionElement(name = "attribute", doc = "Simple attribute projection, with no aggregation function.")
    protected static class Attribute extends SelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.NONE);
        }
    }

    @DefinitionElement(name = "count", doc = "Count aggregation.")
    protected static class Count extends SelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.COUNT);
        }
    }

    @DefinitionElement(name = "sum", doc = "Sum aggregation.")
    protected static class Sum extends SelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.SUM);
        }
    }

    @DefinitionElement(name = "avg", doc = "Average aggregation.")
    protected static class Avg extends SelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.AVG);
        }
    }

    @DefinitionElement(name = "min", doc = "Minimum aggregation.")
    protected static class Min extends SelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.MIN);
        }
    }

    @DefinitionElement(name = "max", doc = "Max aggregation.")
    protected static class Max extends SelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.MAX);
        }
    }

    protected static abstract class OrderedSelectExpressionElement extends SelectExpressionElement {
        @Property(doc = "Whether the column should be ordered in ascending order. Default is true, and false means descending.")
        protected boolean asc = true;

        public abstract Queryable.SelectExpression toSelectExpression();
    }

    @DefinitionElement(name = "attribute", doc = "Simple attribute projection, with no aggregation function.")
    protected static class OrderedAttribute extends OrderedSelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.NONE, asc);
        }
    }

    @DefinitionElement(name = "count", doc = "Count aggregation.")
    protected static class OrderedCount extends OrderedSelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.COUNT, asc);
        }
    }

    @DefinitionElement(name = "sum", doc = "Sum aggregation.")
    protected static class OrderedSum extends OrderedSelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.SUM, asc);
        }
    }

    @DefinitionElement(name = "avg", doc = "Average aggregation.")
    protected static class OrderedAvg extends OrderedSelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.AVG, asc);
        }
    }

    @DefinitionElement(name = "min", doc = "Minimum aggregation.")
    protected static class OrderedMin extends OrderedSelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.MIN, asc);
        }
    }

    @DefinitionElement(name = "max", doc = "Max aggregation.")
    protected static class OrderedMax extends OrderedSelectExpressionElement {
        @Override
        public Queryable.SelectExpression toSelectExpression() {
            return new Queryable.SelectExpression(path, Queryable.AggregationFunction.MAX, asc);
        }
    }

    protected static Class<? extends SelectExpressionElement>[]  SELECT_EXPRESSIONS = new Class[] {Attribute.class, Count.class, Sum.class, Avg.class, Min.class, Max.class};
    protected static Class<? extends OrderedSelectExpressionElement>[]  ORDERED_SELECT_EXPRESSIONS = new Class[] {OrderedAttribute.class, OrderedCount.class, OrderedSum.class, OrderedAvg.class, OrderedMin.class, OrderedMax.class};

    protected static class ProjectionConverter extends ReflexiveConverters.ListConverter {
        public ProjectionConverter() {
            super(SELECT_EXPRESSIONS);
        }
    }

    protected static class SelectExpressionConverter extends ReflexiveConverters.ObjectConverter {
        public SelectExpressionConverter() {
            super(SELECT_EXPRESSIONS);
        }
    }

    protected static class AggregatedSortConverter extends ReflexiveConverters.ListConverter {
        public AggregatedSortConverter() {
            super(ORDERED_SELECT_EXPRESSIONS);
        }
    }

    protected static class SortElement {
        public final String attribute;
        public final boolean asc;

        private SortElement(String attribute, boolean asc) {
            this.attribute = attribute;
            this.asc = asc;
        }
    }

    protected static class SortConverter implements Converter<List<SortElement>> {
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
}
