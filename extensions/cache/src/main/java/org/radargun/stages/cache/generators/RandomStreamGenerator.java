package org.radargun.stages.cache.generators;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import org.radargun.config.DefinitionElement;

/**
 * Generator for {@link RandomStream} objects
 * 
 * @author zhostasa
 */
@DefinitionElement(name = "randomStream", doc = "Generates stream of random data, specifically for stream operation testing")
public class RandomStreamGenerator implements ValueGenerator {

   public Object generateValue(Object key, int size, Random random) {
      return new RandomStream(size);
   }

   /**
    * Will attempt to retrieve length of {@link RandomStream}
    * 
    * @return Size of {@link RandomStream} or 0
    */
   public int sizeOf(Object value) {
      if (value instanceof RandomStream)
         return ((RandomStream) value).length;
      return 0;
   }

   /**
    * If value is instance of {@link InputStream} validates the length against
    * expectedSize
    * 
    * @param value
    *           Should be {@link InputStream}
    * @param expectedSize
    *           Expected size of data
    * 
    * @return true if value is {@link InputStream} and returns expectedSize of
    *         values
    */
   public boolean checkValue(Object value, Object key, int expectedSize) {
      int count = 0;
      try (InputStream is = (InputStream) value) {
         while (is.read() != -1)
            count++;
      } catch (IOException e) {
         return false;
      }
      return count == expectedSize;
   }

   /**
    * Stream returning a predetermined count of random values
    * 
    * @author zhostasa
    *
    */
   public class RandomStream extends InputStream {

      private Random random;

      private int length, processed;

      /**
       * 
       * @param length
       *           The number of int values the stream will return
       */
      public RandomStream(int length) {
         random = new Random();
         this.length = length;
         reset();
      }

      /**
       * @return Positive int or -1 upon reaching stream length
       */
      @Override
      public int read() throws IOException {
         return length > processed++ ? Math.abs(random.nextInt() & 0xFF) : -1;
      }

      /**
       * Resets the stream to starting state
       */
      @Override
      public void reset() {
         processed = 0;
         this.random = new Random();
      }

      public int getLength() {
         return length;
      }
   }
}
