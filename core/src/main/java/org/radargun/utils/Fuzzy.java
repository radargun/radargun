package org.radargun.utils;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.radargun.config.Converter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class Fuzzy<T extends Serializable> implements Serializable {
   private Serializable[] values;
   private double[] probability;

   private Fuzzy(Serializable[] values, double[] probability) {
      this.values = values;
      this.probability = probability;
   }

   public static <T extends Serializable> Fuzzy<T> always(T singleValue) {
      return new Fuzzy<T>(new Serializable[] {singleValue}, new double[] {1.0});
   }

   public T next(Random random) {
      if (probability.length == 1) return (T) values[0];
      double x = random.nextDouble();
      int index = Arrays.binarySearch(probability, x);
      if (index < 0) {
         index = -index - 1;
      } else {
         index = index + 1;
      }
      return (T) values[index];
   }

   public Map<T, Double> getProbabilityMap() {
      HashMap<T, Double> map = new HashMap<T, Double>();
      for (int i = 0; i < values.length; ++i) {
         map.put((T) values[i], probability[i]);
      }
      return map;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < values.length; ++i) {
         if (i != 0) sb.append(", ");
         sb.append(String.format("%.1f%%: ", (probability[i] - (i == 0 ? .0 : probability[i - 1])) * 100));
         sb.append(values[i]);
      }
      return sb.append("]").toString();
   }

   public static class Builder<T extends Serializable> {
      private ArrayList<T> weightedValues = new ArrayList<T>();
      private ArrayList<Double> weights = new ArrayList<Double>();
      private ArrayList<T> fixedValues = new ArrayList<T>();
      private ArrayList<Double> probabilities = new ArrayList<Double>();

      public Builder addWeighted(T value, double weight) {
         if (weight <= 0) throw new IllegalArgumentException();
         weightedValues.add(value);
         weights.add(weight);
         return this;
      }

      public Builder addFixed(T value, double probability) {
         if (probability <= 0 || probability > 1) throw new IllegalArgumentException();
         fixedValues.add(value);
         probabilities.add(probability);
         return this;
      }

      public Fuzzy<T> create() {
         if (weightedValues.size() + fixedValues.size() == 0) throw new IllegalStateException();
         double sumWeight = 0;
         for (Double w : this.weights) {
            sumWeight += w;
         }
         double[] cumulativeProbability = new double[this.probabilities.size() + this.weights.size()];
         int i = 0;
         double cumulatedProbability = 0;
         for (Double p : this.probabilities) {
            cumulatedProbability += p;
            cumulativeProbability[i++] = cumulatedProbability;
         }
         if (cumulatedProbability > 1
            || (cumulatedProbability == 1 && weights.size() > 0)
            || (cumulatedProbability < 0.99999 && weights.size() == 0)) {
            throw new IllegalStateException("Probability: " + cumulatedProbability);
         }
         double cumulatedWeight = 0;
         for (Double w : this.weights) {
            cumulatedWeight += w;
            cumulativeProbability[i++] = (1 - cumulatedProbability) * cumulatedWeight / sumWeight;
         }
         // we can't trust doubles
         cumulativeProbability[cumulativeProbability.length - 1] = 1.0;
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
               builder.addWeighted(parse(part.trim()), 1.0);
            } else {
               int percent = part.indexOf('%');
               if (percent >= 0 && percent < colon) {
                  double probability = Double.parseDouble(part.substring(0, percent).trim()) / 100;
                  builder.addFixed(parse(part.substring(colon + 1).trim()), probability);
               } else {
                  double weight = Double.parseDouble(part.substring(0, colon).trim());
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
