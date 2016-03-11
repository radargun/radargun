package org.radargun.stats;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

import org.radargun.config.ComplexDefinition;
import org.radargun.config.DefinitionElementConverter;
import org.radargun.utils.ReflexiveConverters;

/**
 * Records request for single operation and converts the data stored into requested representation.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface OperationStats extends Serializable {
   /**
    * @return New instance of the same type & settings
    */
   OperationStats newInstance();

   /**
    * @return Deep copy of this instance.
    */
   OperationStats copy();

   /**
    * Adds the data stored by another instance to this instance.
    * @param other Must be of the same class as this instance.
    */
   void merge(OperationStats other);

   /**
    * @param request
    */
   void record(Request request);

   /**
    * @param message
    */
   void record(Message message);

   /**
    * @param requestSet
    */
   void record(RequestSet requestSet);

   /**
    * Convert the internal state into requested representation.
    * @param clazz
    * @param args
    * @return The representation, or null if this class is not capable of requested conversion.
    */
   <T> T getRepresentation(Class<T> clazz, Object... args);

   /**
    * @return True if no request was recorded.
    */
   boolean isEmpty();

   public static class Converter implements DefinitionElementConverter<OperationStats> {
      private ReflexiveConverters.ListConverter converter = new ReflexiveConverters.ListConverter(OperationStats.class);

      @Override
      public OperationStats convert(ComplexDefinition definition, Type type) {
         List<OperationStats> list = converter.convert(definition, List.class);
         if (list.isEmpty()) throw new IllegalStateException();
         if (list.size() == 1) return list.get(0);
         return new MultiOperationStats(list.toArray(new OperationStats[list.size()]));
      }

      @Override
      public String convertToString(OperationStats value) {
         return String.valueOf(value);
      }

      @Override
      public Collection<Class<?>> content() {
         return converter.content();
      }

      @Override
      public int minAttributes() {
         return 1;
      }

      @Override
      public int maxAttributes() {
         return -1;
      }
   }
}
