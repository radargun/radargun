package org.radargun.config;

/**
 * Interface that converts string representation of a collection of objects into java.util.Collection
 *
 * @author rvansa
 * @since 4.0
 */
public interface Converter<T> {
   public T convert(String string);
   public String convertToString(T value);

   /**
    * @return Regexp pattern of allowed input strings
    */
   public String allowedPattern();

   /**
    * This is a placeholder because Property annotation does not allow null default value for converter.
    */
   public static final class DefaultConverter implements Converter<Object> {
      @Override
      public Object convert(String string) {
         throw new UnsupportedOperationException();
      }

      @Override
      public String convertToString(Object value) {
         throw new UnsupportedOperationException();
      }

      @Override
      public String allowedPattern() {
         throw new UnsupportedOperationException();
      }
   }
}
