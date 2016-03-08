package org.radargun.stages.query;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.traits.Query;
import org.radargun.utils.NumberConverter;
import org.radargun.utils.ObjectConverter;
import org.radargun.utils.RandomValue;
import org.radargun.utils.ReflexiveConverters;

/**
 * Definition elements that formulate the condition that should be
 * used in {@link org.radargun.traits.Query.Builder}.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class Condition {
   public abstract void apply(Query.Builder builder);

   public String toString() {
      return PropertyHelper.getDefinitionElementName(getClass()) + PropertyHelper.toString(this);
   }

   public abstract static class PathCondition extends Condition {
      @Property(doc = "Target path (field on the queried object or path through embedded objects)", optional = false)
      public String path;
   }

   @DefinitionElement(name = "eq", doc = "Target is equal to value")
   public static class Eq extends PathCondition {
      @Property(doc = "Value used in the condition.", converter = ObjectConverter.class)
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

   public abstract static class PathNumberCondition extends PathCondition {
      @Property(doc = "Value used in the condition", converter = NumberConverter.class)
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
   public static class Lt extends PathNumberCondition {
      @Override
      public void apply(Query.Builder builder) {
         builder.lt(path, getValue());
      }
   }

   @DefinitionElement(name = "le", doc = "Target is <= than value")
   public static class Le extends PathNumberCondition {
      @Override
      public void apply(Query.Builder builder) {
         builder.le(path, getValue());
      }
   }

   @DefinitionElement(name = "gt", doc = "Target is > than value")
   public static class Gt extends PathNumberCondition {
      @Override
      public void apply(Query.Builder builder) {
         builder.gt(path, getValue());
      }
   }

   @DefinitionElement(name = "ge", doc = "Target is >= than value")
   public static class Ge extends PathNumberCondition {
      @Override
      public void apply(Query.Builder builder) {
         builder.ge(path, getValue());
      }
   }

   @DefinitionElement(name = "between", doc = "Target is between two values")
   public static class Between extends PathCondition {
      @Property(doc = "Lower bound for the value", converter = NumberConverter.class)
      public Number lowerBound;

      @Property(doc = "Random lower bound to be generated during query build time.", complexConverter = RandomValue.NumberConverter.class)
      public RandomValue<Number> randomLowerBound;

      @Property(doc = "Does the range include the lower-bound? Default is true.")
      public boolean lowerInclusive = true;

      @Property(doc = "Upper bound for the value", converter = NumberConverter.class)
      public Number upperBound;

      @Property(doc = "Random upper bound to be generated during query build time.", complexConverter = RandomValue.NumberConverter.class)
      public RandomValue<Number> randomUpperBound;

      @Property(doc = "Does the range include the upper-bound? Default is true.")
      public boolean upperInclusive = true;

      @Init
      public void init() {
         if (lowerBound == null && randomLowerBound == null)
            throw new IllegalStateException("Define either lowerBound or randomLowerBound");
         if (lowerBound != null && randomLowerBound != null)
            throw new IllegalStateException("Define one of: lowerBound, randomLowerBound");
         if (upperBound == null && randomUpperBound == null)
            throw new IllegalStateException("Define either upperBound or randomUpperBound");
         if (upperBound != null && randomUpperBound != null)
            throw new IllegalStateException("Define one of: upperBound, randomUpperBound");
      }

      @Override
      public void apply(Query.Builder builder) {
         Number lowerBound = this.lowerBound != null ? this.lowerBound : randomLowerBound.nextValue(ThreadLocalRandom.current());
         Number upperBound = this.upperBound != null ? this.upperBound : randomUpperBound.nextValue(ThreadLocalRandom.current());
         builder.between(path, lowerBound, lowerInclusive, upperBound, upperInclusive);
      }
   }

   @DefinitionElement(name = "like", doc = "Target string matches the value")
   public static class Like extends PathCondition {
      @Property(doc = "Value used in the condition")
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
   public static class Contains extends PathCondition {
      @Property(doc = "Value used in the condition", converter = ObjectConverter.class)
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
   public static class IsNull extends PathCondition {
      @Override
      public void apply(Query.Builder builder) {
         builder.isNull(path);
      }
   }

   @DefinitionElement(name = "not", doc = "All inner conditions are false", resolveType = DefinitionElement.ResolveType.PASS_BY_DEFINITION)
   public static class Not extends Condition {
      @Property(name = "", doc = "Inner conditions", complexConverter = ConditionConverter.class)
      public final List<Condition> subs = new ArrayList<>();

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
   public static class Any extends Condition {
      @Property(name = "", doc = "Inner conditions", complexConverter = ConditionConverter.class)
      public final List<Condition> subs = new ArrayList<>();

      @Override
      public void apply(Query.Builder builder) {
         Query.Builder[] subBuilders = new Query.Builder[subs.size()];
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
   public static class All extends Condition {
      @Property(name = "", doc = "Inner conditions", complexConverter = ConditionConverter.class)
      public final List<Condition> subs = new ArrayList<>();

      @Override
      public void apply(Query.Builder builder) {
         for (Condition sub : subs) {
            sub.apply(builder);
         }
      }
   }

   public static class ConditionConverter extends ReflexiveConverters.ListConverter {
      public ConditionConverter() {
         super(new Class[] {Eq.class, Lt.class, Le.class, Gt.class, Ge.class, Between.class, Like.class, Contains.class, IsNull.class, Not.class, Any.class, All.class});
      }
   }
}
