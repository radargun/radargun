package org.radargun.config;

import java.lang.reflect.Type;

/**
 * Interface that converts string representation of a collection of objects into java.util.Collection
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
