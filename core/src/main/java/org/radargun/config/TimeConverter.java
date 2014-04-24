package org.radargun.config;

import java.lang.reflect.Type;

import org.radargun.utils.Utils;

/**
 * Converts string with time suffix into milliseconds
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
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
