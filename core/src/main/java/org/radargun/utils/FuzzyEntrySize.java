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

/**
 * Maintains variable size for entries. Each size is given a certain probability
 * that defines how often this entry size will be used in a test.
 *
 * This class is related to entry-size attribute in the benchmark file.
 * Example:
 *
 *    entry-size="10%: 10, 20%: 100, 40%: 1000, 20%: 10000, 10%: 100000"
 *
 * See FuzzyConverterTest for more examples.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 *
 */
public final class FuzzyEntrySize implements Serializable {
   private Integer[] values;
   private BigDecimal[] probabilities;

   private FuzzyEntrySize(Integer[] values, BigDecimal[] probabilities) {
      this.values = values;
      this.probabilities = probabilities;
   }

   public static FuzzyEntrySize uniformEntrySize(Integer entrySize) {
      return new FuzzyEntrySize(new Integer[] {entrySize}, new BigDecimal[] { new BigDecimal(1.0)});
   }

   public Integer next(Random random) {
      if (probabilities.length == 1) return values[0];
      double x = random.nextDouble();
      int index = Arrays.binarySearch(probabilities, x);
      if (index < 0) {
         index = -index - 1;
      } else {
         index = index + 1;
      }
      return values[index];
   }

   public Map<Integer, BigDecimal> getProbabilityMap() {
      Map<Integer, BigDecimal> map = new HashMap<Integer, BigDecimal>();
      for (int i = 0; i < values.length; ++i) {
         map.put(values[i], probabilities[i]);
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

   public static class Builder {
      private ArrayList<Integer> weightedValues = new ArrayList<>();
      private ArrayList<BigDecimal> weights = new ArrayList<BigDecimal>();
      private ArrayList<Integer> fixedValues = new ArrayList<>();
      private ArrayList<BigDecimal> probabilities = new ArrayList<BigDecimal>();

      public Builder addWeighted(Integer entrySize, BigDecimal weight) {
         //less then 0
         if (weight.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException();
         weightedValues.add(entrySize);
         weights.add(weight);
         return this;
      }

      public Builder addFixed(Integer entrySize, BigDecimal probability) {
         if (!isValid(probability)) throw new IllegalArgumentException();
         fixedValues.add(entrySize);
         probabilities.add(probability);
         return this;
      }

      private boolean isValid(BigDecimal probability) {
         //smaller than 0 or greater than 1
         if (probability.compareTo(BigDecimal.ZERO) <= 0 || probability.compareTo(BigDecimal.ONE) == 1)
            return false;
         else
            return true;
      }

      public FuzzyEntrySize create() {
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
         ArrayList<Integer> values = new ArrayList<>(fixedValues.size() + weightedValues.size());
         values.addAll(fixedValues);
         values.addAll(weightedValues);
         return new FuzzyEntrySize(values.toArray(new Integer[values.size()]), cumulativeProbability);
      }
   }

   public static class FuzzyConverter implements org.radargun.config.Converter<FuzzyEntrySize> {
      @Override
      public FuzzyEntrySize convert(String string, Type type) {
         string = string.trim();
         if (string.startsWith("[") && string.endsWith("]")) {
            string = string.substring(1, string.length() - 1);
         }
         String[] parts = string.split(",", 0);
         Builder builder = new Builder();
         for (String part : parts) {
            int colon = part.indexOf(':');
            if (colon < 0) {
               builder.addWeighted(Integer.parseInt(part.trim()), BigDecimal.ONE);
            } else {
               int percent = part.indexOf('%');
               if (percent >= 0 && percent < colon) {
                  BigDecimal probability = new BigDecimal(part.substring(0, percent).trim()).divide(BigDecimal.valueOf(100));
                  builder.addFixed(Integer.parseInt(part.substring(colon + 1).trim()), probability);
               } else {
                  BigDecimal weight = new BigDecimal(part.substring(0, colon).trim());
                  builder.addWeighted(Integer.parseInt(part.substring(colon + 1).trim()), weight);
               }
            }
         }
         return builder.create();
      }

      public String allowedPattern(Type type) {
         return "\\s*([0-9.]+(\\.[0-9]*)?\\s*%?\\s*:\\s*)?" + getPattern()
            + "\\s*(,\\s*([0-9.]+(\\.[0-9]*)?\\s*%?\\s*:\\s*)?" + getPattern() + "\\s*)*";
      }

      protected String getPattern() {
         return "[0-9]+";
      }

      public String convertToString(FuzzyEntrySize value) {
         if (value == null) return "null";
         else return value.toString();
      }
   }
}
