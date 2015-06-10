package org.radargun.utils;

import java.text.DecimalFormat;

/**
 * Computes min/max.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class MinMax {
   public static class Long {
      long min = java.lang.Long.MAX_VALUE;
      long max = java.lang.Long.MIN_VALUE;

      public long min() {
         return min;
      }

      public long max() {
         return max;
      }

      public Long add(long value) {
         min = Math.min(min, value);
         max = Math.max(max, value);
         return this;
      }

      public String toString() {
         return toString(min, max);
      }

      public static String toString(long min, long max) {
         if (min > max) return "none";
         if (min == max) return String.valueOf(max);
         return min + " .. " + max;
      }

      public boolean isSet() {
         return min <= max;
      }
   }

   public static class Int {
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;

      public int min() {
         return min;
      }

      public int max() {
         return max;
      }

      public Int add(int value) {
         min = Math.min(min, value);
         max = Math.max(max, value);
         return this;
      }

      public String toString() {
         return toString(min, max);
      }

      public static String toString(int min, int max) {
         if (min > max) return "none";
         if (min == max) return String.valueOf(max);
         return min + " .. " + max;
      }

      public boolean isSet() {
         return min <= max;
      }
   }

   public static class Double {
      double min = java.lang.Double.POSITIVE_INFINITY;
      double max = java.lang.Double.NEGATIVE_INFINITY;

      public double min() {
         return min;
      }

      public double max() {
         return max;
      }

      public Double add(double value) {
         min = Math.min(min, value);
         max = Math.max(max, value);
         return this;
      }

      public String toString() {
         return toString(min, max);
      }

      public static String toString(double min, double max) {
         if (min > max) return "none";
         if (min == max) return String.valueOf(max);
         return min + " .. " + max;
      }

      public String toString(DecimalFormat formatter) {
         if (min > max) return "none";
         if (min == max) return formatter.format(max);
         return formatter.format(min) + " .. " + formatter.format(max);
      }

      public boolean isSet() {
         return !java.lang.Double.isInfinite(min);
      }
   }
}
