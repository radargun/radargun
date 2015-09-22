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
 */
public abstract class AbstractQueryStage extends TestStage {

    @Property(optional = false, doc = "Full class name of the object that should be queried. Mandatory.")
    protected String queryObjectClass;

    @Property(optional = false, doc = "Conditions used in the query", complexConverter = ConditionConverter.class)
    protected List<Condition> conditions;

    @Property(doc = "Use projection instead of returning full object. Default is without projection.")
    protected String[] projection;

    @Property(doc = "Use sorting order, in form [attribute[:(ASC|DESC)]][,attribute[:(ASC|DESC)]]*. " +
            "Without specifying ASC or DESC the sort order defaults to ASC. Default is unordereded.",
            converter = SortConverter.class)
    protected List<SortElement> orderBy;

    @Property(doc = "Offset in the results. Default is none.")
    protected long offset = -1;

    @Property(doc = "Maximum number of the results. Default is none.")
    protected long limit = -1;

    @Property(doc = "Check whether all slaves got the same result, and fail if not. Default is false.")
    protected boolean checkSameResult = false;

    @InjectTrait
    protected Queryable queryable;

    protected AtomicInteger expectedSize = new AtomicInteger(-1);

    public abstract OperationLogic getLogic();

    protected static abstract class Condition {
        public abstract void apply(Queryable.QueryBuilder builder);

        public String toString() {
            return PropertyHelper.getDefinitionElementName(getClass()) + PropertyHelper.toString(this);
        }
    }

    protected static abstract class PathCondition extends Condition {
        @Property(doc = "Target path (field on the queried object or path through embedded objects)", optional = false)
        public String path;
    }

    @DefinitionElement(name = "eq", doc = "Target is equal to value")
    protected static class Eq extends PathCondition {
        @Property(doc = "Value used in the condition", optional = false, converter = ObjectConverter.class)
        public Object value;

        @Override
        public void apply(Queryable.QueryBuilder builder) {
            builder.eq(path, value);
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
            builder.lt(path, value);
        }
    }

    @DefinitionElement(name = "le", doc = "Target is <= than value")
    protected static class Le extends PathNumberCondition {
        @Override
        public void apply(Queryable.QueryBuilder builder) {
            builder.le(path, value);
        }
    }

    @DefinitionElement(name = "gt", doc = "Target is > than value")
    protected static class Gt extends PathNumberCondition {
        @Override
        public void apply(Queryable.QueryBuilder builder) {
            builder.gt(path, value);
        }
    }

    @DefinitionElement(name = "ge", doc = "Target is < than value")
    protected static class Ge extends PathNumberCondition {
        @Override
        public void apply(Queryable.QueryBuilder builder) {
            builder.ge(path, value);
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
            builder.between(path, lowerBound, lowerInclusive, upperBound, upperInclusive);
        }
    }

    @DefinitionElement(name = "like", doc = "Target string matches the value")
    protected static class Like extends PathCondition {
        @Property(doc = "Value used in the condition", optional = false)
        public String value;

        @Override
        public void apply(Queryable.QueryBuilder builder) {
            builder.like(path, value);
        }
    }

    @DefinitionElement(name = "contains", doc = "Target is collection containing the value")
    protected static class Contains extends PathCondition {
        @Property(doc = "Value used in the condition", optional = false, converter = ObjectConverter.class)
        public Object value;

        @Override
        public void apply(Queryable.QueryBuilder builder) {
            builder.contains(path, value);
        }
    }

    @DefinitionElement(name = "is-null", doc = "Target is not defined (null)")
    protected static class IsNull extends PathCondition {
        @Override
        public void apply(Queryable.QueryBuilder builder) {
            builder.isNull(path);
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

    @DefinitionElement(name = "all", doc = "All inner conditions are false", resolveType = DefinitionElement.ResolveType.PASS_BY_DEFINITION)
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
