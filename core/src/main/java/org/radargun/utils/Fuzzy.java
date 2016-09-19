package org.radargun.utils;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.radargun.config.Converter;

/**
 * Represents <a href="https://en.wikipedia.org/wiki/Probability_mass_function">probability mass function</a>,
 * a random variable providing values from given set with defined (different) probabilities.
 * <p>
 * The {@link Builder} allows to configure the values either using fixed probabilities (given value
 * will be returned with e.g. 10% probability) or using weighting (if one value is added with weight 2
 * while others with weight 1, this value will be returned twice as often).
 * <p>
 * If fixed probabilities and weights are combined, the probability of weighted values is computed from
 * 1 - (sum of fixed probabilities).
 * <p>
 * Example:
 * <pre>
 * {@code new Builder().addFixed(1, 0.2).addFixed(2, 0.3).addWeighted(3, 1).addWeighted(4, 4),build() }
 * </pre>
 *
 * will return a random variable that will return values with following probabilities:
 * <table>
 * <tr><td> 1 </td><td> 20% </td></tr>
 * <tr><td> 2 </td><td> 30% </td></tr>
 * <tr><td> 3 </td><td> 10% </td></tr>
 * <tr><td> 4 </td><td> 40% </td></tr>
 * </table>
 *
 * The {@link IntegerConverter} provides a convenient way to use this as a property (specialized for integer values),
 * the previous example would be configured as:
 *
 * <pre>
 * {@code fuzzy-property="20%: 1, 30%: 2, 1: 3, 4: 4"}
 * </pre>
 * <p>
 * Note that if the weight is not specified, it defaults to one.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class Fuzzy<T extends Serializable> implements Serializable {
   private Serializable[] values;
   private BigDecimal[] probabilities;

   private Fuzzy(Serializable[] values, BigDecimal[] probabilities) {
      this.values = values;
      this.probabilities = probabilities;
   }

   public static <T extends Serializable> Fuzzy<T> uniform(T value) {
      return new Fuzzy<T>(new Serializable[] {value}, new BigDecimal[] { new BigDecimal(1.0)});
   }

   public T next(Random random) {
      if (probabilities.length == 1) return (T) values[0];
      BigDecimal x = BigDecimal.valueOf(random.nextDouble());
      int index = Arrays.binarySearch(probabilities, x);
      if (index < 0) {
         index = -index - 1;
      } else {
         index = index + 1;
      }
      return (T) values[index];
   }

   public Map<T, BigDecimal> getProbabilityMap() {
      Map<T, BigDecimal> map = new HashMap<T, BigDecimal>();
      for (int i = 0; i < values.length; ++i) {
         map.put((T) values[i], probabilities[i]);
      }
      return map;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < values.length; ++i) {
         if (i != 0) sb.append(", ");
         BigDecimal previousProbability = (i == 0 ? BigDecimal.ZERO : probabilities[i - 1]);
         sb.append(String.format("%.1f%%: ", probabilities[i].subtract(previousProbability).multiply(BigDecimal.valueOf(100))));
         sb.append(values[i]);
      }
      return sb.append("]").toString();
   }

   public static class Builder<T extends Serializable> {
      private ArrayList<T> weightedValues = new ArrayList<T>();
      private ArrayList<BigDecimal> weights = new ArrayList<BigDecimal>();
      private ArrayList<T> fixedValues = new ArrayList<T>();
      private ArrayList<BigDecimal> probabilities = new ArrayList<BigDecimal>();

      public Builder addWeighted(T value, BigDecimal weight) {
         //less then 0
         if (weight.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException();
         weightedValues.add(value);
         weights.add(weight);
         return this;
      }

      public Builder addFixed(T value, BigDecimal probability) {
         if (!isValid(probability)) throw new IllegalArgumentException();
         fixedValues.add(value);
         probabilities.add(probability);
         return this;
      }

      private boolean isValid(BigDecimal probability) {
         // greater than 0 but smaller or equal to 1
         return probability.compareTo(BigDecimal.ZERO) == 1 && probability.compareTo(BigDecimal.ONE) != 1;
      }

      public Fuzzy<T> create() {
         if (weightedValues.size() + fixedValues.size() == 0) throw new IllegalStateException();
         BigDecimal sumWeight = BigDecimal.ZERO;
         for (BigDecimal w : this.weights) {
            sumWeight = sumWeight.add(w);
         }
         BigDecimal[] cumulativeProbability = new BigDecimal[this.probabilities.size() + this.weights.size()];
         int i = 0;
         BigDecimal cumulatedProbability = BigDecimal.ZERO;
         for (BigDecimal p : this.probabilities) {
            cumulatedProbability = cumulatedProbability.add(p);
            cumulativeProbability[i++] = cumulatedProbability;
         }
         if (cumulatedProbability.compareTo(BigDecimal.ONE) == 1 //bigger than 1
            || (cumulatedProbability.compareTo(BigDecimal.ONE) == 0 && weights.size() > 0) //equal to one
            || (cumulatedProbability.compareTo(BigDecimal.ONE) == -1 && weights.size() == 0)) { //less than one
            throw new IllegalStateException("Probability: " + cumulatedProbability);
         }
         BigDecimal cumulatedWeight = BigDecimal.ZERO;
         MathContext mc = new MathContext(2, RoundingMode.HALF_UP); //required for division
         for (BigDecimal w : this.weights) {
            cumulatedWeight = cumulatedWeight.add(w);
            BigDecimal currentCumulativeProbability = (BigDecimal.ONE.subtract(cumulatedProbability)).multiply(cumulatedWeight).divide(sumWeight, mc);
            BigDecimal previousCumulativeProbability = (i == 0 ? BigDecimal.ZERO : cumulativeProbability[i - 1]);
            cumulativeProbability[i++] = previousCumulativeProbability.add(currentCumulativeProbability);
         }
         cumulativeProbability[cumulativeProbability.length - 1] = new BigDecimal(1.0);
         ArrayList<T> values = new ArrayList<T>(fixedValues.size() + weightedValues.size());
         values.addAll(fixedValues);
         values.addAll(weightedValues);
         return new Fuzzy<T>(values.toArray(new Serializable[values.size()]), cumulativeProbability);
      }
   }

   private abstract static class FuzzyConverter<T extends Serializable> implements Converter<Fuzzy<T>> {
      @Override
      public Fuzzy<T> convert(String string, Type type) {
         string = string.trim();
         if (string.startsWith("[") && string.endsWith("]")) {
            string = string.substring(1, string.length() - 1);
         }
         String[] parts = string.split(",", 0);
         Builder<T> builder = new Builder<T>();
         for (String part : parts) {
            int colon = part.indexOf(':');
            if (colon < 0) {
               builder.addWeighted(parse(part.trim()), BigDecimal.ONE);
            } else {
               int percent = part.indexOf('%');
               if (percent >= 0 && percent < colon) {
                  BigDecimal probability = new BigDecimal(part.substring(0, percent).trim()).divide(BigDecimal.valueOf(100));
                  builder.addFixed(parse(part.substring(colon + 1).trim()), probability);
               } else {
                  BigDecimal weight = new BigDecimal(part.substring(0, colon).trim());
                  builder.addWeighted(parse(part.substring(colon + 1).trim()), weight);
               }
            }
         }
         return builder.create();
      }

      @Override
      public String convertToString(Fuzzy<T> value) {
         if (value == null) return "null";
         else return value.toString();
      }

      @Override
      public String allowedPattern(Type type) {
         return "\\s*([0-9.]+(\\.[0-9]*)?\\s*%?\\s*:\\s*)?" + getPattern()
            + "\\s*(,\\s*([0-9.]+(\\.[0-9]*)?\\s*%?\\s*:\\s*)?" + getPattern() + "\\s*)*";
      }

      protected abstract T parse(String string);

      protected abstract String getPattern();
   }

   public static class IntegerConverter extends FuzzyConverter<Integer> {
      @Override
      protected Integer parse(String string) {
         return Integer.parseInt(string);
      }

      @Override
      protected String getPattern() {
         return "[0-9]+";
      }
   }
}
