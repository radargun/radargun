package org.radargun.stages.cache.generators;

import java.nio.ByteBuffer;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;

/**
 * @author Roman Macor (rmacor@redhat.com)
 */
@DefinitionElement(name = "byte-array-key", doc = "Generates byte-array keys")
public class ByteArrayKeyGenerator implements KeyGenerator {

   @Property(doc = "Size of the key in bytes. Default is the number of bytes used to represent a {@code long} " +
      "value in two's complement binary form.")
   protected int keySize = Long.BYTES;

   @Override
   public Object generateKey(long keyIndex) {
      ByteBuffer buffer = ByteBuffer.allocate(keySize);
      buffer.putLong(keyIndex);
      return buffer.array();
   }
}
