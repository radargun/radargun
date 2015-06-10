package org.radargun.stages.cache.test;

import org.radargun.config.Converter;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.TestStage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;
import org.radargun.utils.NumberConverter;
import org.radargun.utils.ObjectConverter;
import org.radargun.utils.RandomValue;
import org.radargun.utils.ReflexiveConverters;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
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
    
    @InjectTrait
    protected Queryable queryable;

    protected AtomicInteger expectedSize = new AtomicInteger(-1);

    public abstract OperationLogic getLogic();

    protected static abstract class Condition {
        public abstract void apply(Query.Builder builder);

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

        @Property(doc = "Random value to be generated during query build time.", complexConverter = RandomValue.PrimitiveConverter.class)
        public RandomValue random;

        @Init
        public void init() {
            if (value == null && random == null) throw new IllegalStateException("Define either value or random");
            if (value != null && random != null) throw new IllegalStateException("Define one of: value, random");
        }

        @Override
        public void apply(Query.Builder builder) {
            builder.eq(path, value != null ? value : random.nextValue(ThreadLocalRandom.current()));
        }
    }

    protected static abstract class PathNumberCondition extends PathCondition {
        @Property(doc = "Value used in the condition", optional = false, converter = NumberConverter.class)
        public Number value;

        @Property(doc = "Random value to be generated during query build time.", complexConverter = RandomValue.NumberConverter.class)
        public RandomValue<Number> random;

        @Init
        public void init() {
            if (value == null && random == null) throw new IllegalStateException("Define either value or random");
            if (value != null && random != null) throw new IllegalStateException("Define one of: value, random");
        }

        protected Number getValue() {
            return value != null ? value : random.nextValue(ThreadLocalRandom.current());
        }
    }

    @DefinitionElement(name = "lt", doc = "Target is < than value")
    protected static class Lt extends PathNumberCondition {
        @Override
        public void apply(Query.Builder builder) {
            builder.lt(path, getValue());
        }
    }

    @DefinitionElement(name = "le", doc = "Target is <= than value")
    protected static class Le extends PathNumberCondition {
        @Override
        public void apply(Query.Builder builder) {
            builder.le(path, getValue());
        }
    }

    @DefinitionElement(name = "gt", doc = "Target is > than value")
    protected static class Gt extends PathNumberCondition {
        @Override
        public void apply(Query.Builder builder) {
            builder.gt(path, getValue());
        }
    }

    @DefinitionElement(name = "ge", doc = "Target is < than value")
    protected static class Ge extends PathNumberCondition {
        @Override
        public void apply(Query.Builder builder) {
            builder.ge(path, getValue());
        }
    }

    @DefinitionElement(name = "between", doc = "Target is between two values")
    protected static class Between extends PathCondition {
        @Property(doc = "Lower bound for the value", optional = false, converter = NumberConverter.class)
        public Number lowerBound;

        @Property(doc = "Random lower bound to be generated during query build time.", complexConverter = RandomValue.NumberConverter.class)
        public RandomValue<Number> randomLowerBound;

        @Property(doc = "Does the range include the lower-bound? Default is true.")
        public boolean lowerInclusive = true;

        @Property(doc = "Upper bound for the value", optional = false, converter = NumberConverter.class)
        public Number upperBound;

        @Property(doc = "Random upper bound to be generated during query build time.", complexConverter = RandomValue.NumberConverter.class)
        public RandomValue<Number> randomUpperBound;

        @Property(doc = "Does the range include the upper-bound? Default is true.")
        public boolean upperInclusive = true;

        @Init
        public void init() {
            if (lowerBound == null && randomLowerBound == null) throw new IllegalStateException("Define either lowerBound or randomLowerBound");
            if (lowerBound != null && randomLowerBound != null) throw new IllegalStateException("Define one of: lowerBound, randomLowerBound");
            if (upperBound == null && randomUpperBound == null) throw new IllegalStateException("Define either upperBound or randomUpperBound");
            if (upperBound != null && randomUpperBound != null) throw new IllegalStateException("Define one of: upperBound, randomUpperBound");
        }

        @Override
        public void apply(Query.Builder builder) {
            Number lowerBound = this.lowerBound != null ? this.lowerBound : randomLowerBound.nextValue(ThreadLocalRandom.current());
            Number upperBound = this.upperBound != null ? this.upperBound : randomUpperBound.nextValue(ThreadLocalRandom.current());
            builder.between(path, lowerBound, lowerInclusive, upperBound, upperInclusive);
        }
    }

    @DefinitionElement(name = "like", doc = "Target string matches the value")
    protected static class Like extends PathCondition {
        @Property(doc = "Value used in the condition", optional = false)
        public String value;

        @Property(doc = "Random value to be generated during query build time.", complexConverter = RandomValue.StringConverter.class)
        public RandomValue<String> random;

        @Init
        public void init() {
            if (value == null && random == null) throw new IllegalStateException("Define either value or random");
            if (value != null && random != null) throw new IllegalStateException("Define one of: value, random");
        }

        @Override
        public void apply(Query.Builder builder) {
            builder.like(path, value != null ? value : random.nextValue(ThreadLocalRandom.current()));
        }
    }

    @DefinitionElement(name = "contains", doc = "Target is collection containing the value")
    protected static class Contains extends PathCondition {
        @Property(doc = "Value used in the condition", optional = false, converter = ObjectConverter.class)
        public Object value;

        @Property(doc = "Random value to be generated during query build time.", complexConverter = RandomValue.PrimitiveConverter.class)
        public RandomValue<Object> random;

        @Init
        public void init() {
            if (value == null && random == null) throw new IllegalStateException("Define either value or random");
            if (value != null && random != null) throw new IllegalStateException("Define one of: value, random");
        }

        @Override
        public void apply(Query.Builder builder) {
            builder.contains(path, value != null ? value : random.nextValue(ThreadLocalRandom.current()));
        }
    }

    @DefinitionElement(name = "is-null", doc = "Target is not defined (null)")
    protected static class IsNull extends PathCondition {
        @Override
        public void apply(Query.Builder builder) {
            builder.isNull(path);
        }
    }

    @DefinitionElement(name = "not", doc = "All inner conditions are false", resolveType = DefinitionElement.ResolveType.PASS_BY_DEFINITION)
    protected static class Not extends Condition {
        @Property(name = "", doc = "Inner conditions", complexConverter = ConditionConverter.class)
        public final List<Condition> subs = new ArrayList<Condition>();

        @Override
        public void apply(Query.Builder builder) {
            Query.Builder subBuilder = builder.subquery();
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
        public void apply(Query.Builder builder) {
            Query.Builder subBuilders[] = new Query.Builder[subs.size()];
            int i = 0;
            for (Condition sub : subs) {
                Query.Builder subBuilder = builder.subquery();
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
        public void apply(Query.Builder builder) {
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
            return sb.toString().substring(0, sb.length() - 2);
        }

        @Override
        public String allowedPattern(Type type) {
            return "[0-9a-zA-Z_]*(:ASC|:DESC)?(,\\s*[0-9a-zA-Z_]*(:ASC|:DESC)?)*";
        }
    }
}
