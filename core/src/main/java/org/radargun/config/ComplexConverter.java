package org.radargun.config;

import java.lang.reflect.Type;
import java.util.Collection;

/**
 * Converts {@link ComplexDefinition} into object, and also the object into string representation.
 * Provides classes that can be expected as sub-elements in XML for property using this converter.
 */
public interface ComplexConverter<T> {

   T convert(ComplexDefinition definition, Type type);
   String convertToString(T value);

   /**
    * @return Classes that could be the conversion result
    */
   Collection<Class<?>> content();

   /**
    * @return Minimal number of attributes that the definition should contain.
    */
   int minAttributes();

   /**
    * @return Maximal number of attributes that the definition should contain. -1 means unlimited.
    */
   int maxAttributes();

   public static class Dummy implements ComplexConverter<Object> {
      @Override
      public Object convert(ComplexDefinition definition, Type type) {
         throw new UnsupportedOperationException("No complex converter defined for converting this property.");
      }

      @Override
      public String convertToString(Object value) {
         throw new UnsupportedOperationException("No complex converter defined for converting this property.");
      }

      @Override
      public Collection<Class<?>> content() {
         return null;
      }

      @Override
      public int minAttributes() {
         return 0;
      }

      @Override
      public int maxAttributes() {
         return 0;
      }
   }

}
