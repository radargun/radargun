package org.radargun.config;

import org.radargun.utils.Utils;

import java.lang.reflect.Type;

/**
 * Converts string with time suffix into milliseconds
 *
 * @author rvansa
 * @since 2012/12/08
 */
public class TimeConverter implements Converter<Long> {
   @Override
   public Long convert(String string, Type ignored) {
      return Utils.string2Millis(string);
   }

   @Override
   public String convertToString(Long value) {
      return Utils.getMillisDurationString(value);
   }

   @Override
   public String allowedPattern(Type type) {
      return "[0-9]+\\s*[mMsS]?";
   }
}
