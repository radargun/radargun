package org.radargun.config;

import java.lang.reflect.Type;

/**
 * Converts string representation of an object into the object, and back.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
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
