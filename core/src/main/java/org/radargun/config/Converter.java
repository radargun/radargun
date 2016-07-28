package org.radargun.config;

import java.lang.reflect.Type;

/**
 * Converts string representation of an object into the object, and back.
 */
public interface Converter<T> {
   String ANY_MULTI_LINE = "(.|\n|\r)*";

   T convert(String string, Type type);

   String convertToString(T value);

   /**
    * @return Regexp pattern of allowed input strings
    * @param type
    */
   String allowedPattern(Type type);
}
