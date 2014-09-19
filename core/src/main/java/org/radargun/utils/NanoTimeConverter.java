package org.radargun.utils;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

import org.radargun.config.Converter;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class NanoTimeConverter implements Converter<Long> {
   @Override
   public Long convert(String string, Type type) {
      string = string.trim();
      TimeUnit timeUnit;
      if (string.endsWith("ns")) {
         timeUnit = TimeUnit.NANOSECONDS;
         string = string.substring(0, string.length() - 2);
      } else if (string.endsWith("us")) {
         timeUnit = TimeUnit.MICROSECONDS;
         string = string.substring(0, string.length() - 2);
      } else if (string.endsWith("ms")) {
         timeUnit = TimeUnit.MILLISECONDS;
         string = string.substring(0, string.length() - 2);
      } else if (string.endsWith("s")) {
         timeUnit = TimeUnit.SECONDS;
         string = string.substring(0, string.length() - 1);
      } else if (string.endsWith("m")) {
         timeUnit = TimeUnit.MINUTES;
         string = string.substring(0, string.length() - 1);
      } else {
         throw new IllegalArgumentException("No unit specified: " + string);
      }
      string = string.trim();
      try {
         long longValue = Long.parseLong(string);
         return timeUnit.toNanos(longValue);
      } catch (NumberFormatException e) {
         try {
            double doubleValue = Double.parseDouble(string);
            return timeUnit.toNanos((long) (doubleValue * 1000000)) / 1000000;
         } catch (NumberFormatException e2) {
            throw new IllegalArgumentException("Cannot parse value: " + string, e);
         }
      }
   }

   @Override
   public String convertToString(Long value) {
      return value == null ? "null" : String.valueOf(value) + " ns";
   }

   @Override
   public String allowedPattern(Type type) {
      return "[0-9]*(\\.[0-9]*)?\\s*(ns|us|ms|s|m)";
   }
}
