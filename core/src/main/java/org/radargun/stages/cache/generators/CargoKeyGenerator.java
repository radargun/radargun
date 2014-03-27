/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.radargun.stages.cache.generators;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Random;

/**
 * This generator creates key objects with the 8-byte index
 * and random byte-array of configurable length (equal to all keys).
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class CargoKeyGenerator implements KeyGenerator {
   private byte[] cargo;

   @Override
   public void init(String param, ClassLoader classLoader) {
      int keySize = Integer.parseInt(param);
      cargo = new byte[keySize];
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
