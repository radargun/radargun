package org.radargun.stages.cache.generators;

import java.io.Serializable;

import org.radargun.config.DefinitionElement;
import org.radargun.utils.TimeService;
import org.radargun.utils.Timestamped;

@DefinitionElement(name = "timestamp", doc = "Creates key with provided long as an actual key and additional timestamp when key was created")
public class TimestampKeyGenerator implements KeyGenerator {

   @Override
   public Object generateKey(long keyIndex) {
      return new TimestampKey(keyIndex, TimeService.currentTimeMillis());
   }

   public static class TimestampKey implements Timestamped, Serializable {

      private static final long serialVersionUID = 1L;

      private final long key;
      private final long timestamp;

      public TimestampKey(long key, long timestamp) {
         this.key = key;
         this.timestamp = timestamp;
      }

      public long getKey() {
         return key;
      }

      @Override
      public long getTimestamp() {
         return timestamp;
      }

      @Override
      public int hashCode() {
         final int prime = 31;
         int result = 1;
         result = prime * result + (int) (key ^ (key >>> 32));
         return result;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj)
            return true;
         if (obj == null)
            return false;
         if (getClass() != obj.getClass())
            return false;
         TimestampKey other = (TimestampKey) obj;
         if (key != other.key)
            return false;
         return true;
      }
   }

}
