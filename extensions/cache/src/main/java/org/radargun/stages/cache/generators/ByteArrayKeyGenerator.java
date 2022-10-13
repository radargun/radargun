package org.radargun.stages.cache.generators;

import java.nio.ByteBuffer;

import org.radargun.config.DefinitionElement;

/**
 * @author Roman Macor (rmacor@redhat.com)
 */
@DefinitionElement(name = "byte-array-key", doc = "Generates byte-array keys")
public class ByteArrayKeyGenerator implements KeyGenerator {

   @Override
   public Object generateKey(long keyIndex) {
      ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
      buffer.putLong(keyIndex);
      return buffer.array();
   }
}
