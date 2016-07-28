package org.radargun.stages.cache.generators;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Random;

import org.radargun.config.DefinitionElement;

@DefinitionElement(name = "wrapped-array", doc = "Generates random byte array wrapped in object that correctly implements equals() and hashCode().")
public class WrappedArrayValueGenerator implements ValueGenerator {
   @Override
   public Object generateValue(Object key, int size, Random r) {
      return new ByteArrayWrapper(ByteArrayValueGenerator.generateArray(size, r));
   }

   @Override
   public int sizeOf(Object value) {
      return ((ByteArrayWrapper) value).array.length;
   }

   @Override
   public boolean checkValue(Object value, Object key, int expectedSize) {
      if (!(value instanceof ByteArrayWrapper)) return false;
      ByteArrayWrapper wrapper = (ByteArrayWrapper) value;
      return wrapper.array != null && (expectedSize <= 0 || wrapper.array.length == expectedSize);
   }

   /* Because byte[].equals compares only pointers */
   private static class ByteArrayWrapper implements Serializable {
      private byte[] array;
      private transient int hashCode = 0;

      public ByteArrayWrapper(byte[] array) {
         this.array = array;
      }

      @Override
      public int hashCode() {
         if (hashCode == 0) {
            hashCode = 42 + Arrays.hashCode(array);
            if (hashCode == 0) hashCode = 42;
         }
         return hashCode;
      }

      @Override
      public String toString() {
         return String.format("ByteArray[%d](%db)", hashCode(), array.length);
      }

      @Override
      public boolean equals(Object obj) {
         if (obj instanceof ByteArrayWrapper) {
            return Arrays.equals(array, ((ByteArrayWrapper) obj).array);
         }
         return false;
      }
   }
}
