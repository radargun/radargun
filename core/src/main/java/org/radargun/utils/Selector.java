package org.radargun.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Select between multiple weighted options
 */
public class Selector<T> {
   public final int[] ratios;
   public final int max;
   public final T[] options;

   public Selector(int max, T[] options, int[] ratios) {
      this.max = max;
      this.options = options;
      this.ratios = ratios;
   }

   public T select(int value) {
      int index = Arrays.binarySearch(ratios, value);
      if (index < 0) {
         index = -index - 1;
      } else {
         index = index + 1;
      }
      return options[index];
   }

   public static class Builder<T, S extends Selector<T>> {
      private final Class<T> clazz;
      protected int max = 0;
      protected ArrayList<Integer> ratios = new ArrayList<>();
      protected ArrayList<T> options = new ArrayList<>();

      public Builder(Class<T> clazz) {
         this.clazz = clazz;
      }

      public Builder<T, S> add(T op, int ratio) {
         if (ratio == 0) return this;
         if (ratio < 0) throw new IllegalArgumentException("Ratio must be >= 0: " + ratio);
         ratios.add(max + ratio);
         max += ratio;
         options.add(op);
         return this;
      }

      public S build() {
         if (max == 0) throw new IllegalStateException("No operations/ratios defined");
         int[] ratios = new int[this.ratios.size()];
         for (int i = 0; i < ratios.length; ++i) ratios[i] = this.ratios.get(i);
         return newSelector(ratios);
      }

      protected S newSelector(int[] ratios) {
         return (S) new Selector<T>(max, options.toArray((T[]) Array.newInstance(clazz, options.size())), ratios);
      }
   }
}
