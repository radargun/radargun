package org.radargun.config;

import java.lang.reflect.Type;

/**
 * Interface that converts string representation of a collection of objects into java.util.Collection
 *
 * @author rvansa
 * @since 4.0
 */
public interface Converter<T> {
   public T convert(String string, Type type);
   public String convertToString(T value);

   /**
    * @return Regexp pattern of allowed input strings
    * @param type
    */
   public String allowedPattern(Type type);

}
