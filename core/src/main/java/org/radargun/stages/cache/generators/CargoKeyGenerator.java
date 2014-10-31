package org.radargun.stages.cache.generators;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Random;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "cargo", doc = "This generator creates key objects with the 8-byte index" +
      " and random byte-array of configurable length (equal to all keys).")
public class CargoKeyGenerator implements KeyGenerator {
   @Property(doc = "Size of the cargo (in bytes).", optional = false)
   private int size;

   private byte[] cargo;

   @Init
   public void init() {
      cargo = new byte[size];
      new Random().nextBytes(cargo);
   }

   @Override
   public Object generateKey(long keyIndex) {
      return new CargoKey(keyIndex, cargo);
   }

   public static class CargoKey implements Serializable {
      private final long keyIndex;
      private final byte[] cargo;

      public CargoKey(long keyIndex, byte[] cargo) {
         this.keyIndex = keyIndex;
         this.cargo = Arrays.copyOf(cargo, cargo.length);
      }

      public byte[] getCargo() {
         return cargo;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         CargoKey cargoKey = (CargoKey) o;

         if (keyIndex != cargoKey.keyIndex) return false;

         return true;
      }

      @Override
      public int hashCode() {
         return (int) (keyIndex ^ (keyIndex >>> 32));
      }

      @Override
      public String toString() {
         return String.format("CargoKey{keyIndex=%s, cargoSize=%d}", keyIndex, cargo.length);
      }
   }
}
