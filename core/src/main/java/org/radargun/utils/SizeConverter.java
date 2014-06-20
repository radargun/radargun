package org.radargun.utils;

import java.lang.reflect.Type;

import org.radargun.config.Converter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SizeConverter implements Converter<Long> {
   private static final int KILO = 1024;
   private static final int MEGA = 1024 * 1024;
   private static final int GIGA = 1024 * 1024 * 1024;

   @Override
   public Long convert(String string, Type type) {
      string = string.trim();
      if (string.endsWith("b") || string.endsWith("B")) {
         string = string.substring(0, string.length() - 1);
      }
      long multiplier = 1;
      if (string.endsWith("k")) multiplier = KILO;
      else if (string.endsWith("M")) multiplier = MEGA;
      else if (string.endsWith("G")) multiplier = GIGA;
      if (multiplier > 1) {
         string = string.substring(0, string.length() - 1).trim();
      }
      return Long.parseLong(string) * multiplier;
   }

   @Override
   public String convertToString(Long value) {
      if (value == 0) return "0";
      if ((value & (GIGA - 1)) == 0) {
         return String.format("%d GB", value / GIGA);
      } if ((value & (MEGA - 1)) == 0) {
         return String.format("%d MB", value / MEGA);
      } if ((value & (KILO - 1)) == 0) {
         return String.format("%d kB", value / KILO);
      }
      return String.valueOf(value);
   }

   @Override
   public String allowedPattern(Type type) {
      return "[0-9]+\\s*(k[bB]?|M[bB]?|G[bB]?)";
   }
}
