package org.radargun.utils;

import java.lang.reflect.Type;
import java.time.Duration;

import org.radargun.config.Converter;

public class DurationConverter implements Converter<Duration> {
   @Override
   public Duration convert(String string, Type type) {
      return Utils.string2Nanos(string);
   }

   @Override
   public String convertToString(Duration value) {
      return Utils.getNanosDurationString(value.toNanos());
   }

   @Override
   public String allowedPattern(Type type) {
      return "[-0-9]+\\s*[mMsS]?";
   }
}
