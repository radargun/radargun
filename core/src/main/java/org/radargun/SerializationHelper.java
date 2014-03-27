package org.radargun;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Helper class holding serialization logic.
 *
 * @author Mircea.Markus@jboss.com
 */
public class SerializationHelper {
   private static final int MIN_REMAINING = 32;

   public static ByteBuffer serializeObject(Serializable serializable, ByteBuffer buffer) throws IOException {
      ByteBufferOutputStream out = new ByteBufferOutputStream(buffer);
      ObjectOutputStream oos = new ObjectOutputStream(out);
      oos.writeObject(serializable);
      return out.getBuffer();
   }

   public static ByteBuffer serializeObjectWithLength(Serializable serializable, ByteBuffer buffer) throws IOException {
      if (buffer.remaining() < MIN_REMAINING) {
         buffer = grow(buffer, MIN_REMAINING);
      }
      int sizePosition = buffer.position();
      buffer.position(sizePosition + 4);
      ByteBufferOutputStream out = new ByteBufferOutputStream(buffer);
      ObjectOutputStream oos = new ObjectOutputStream(out);
      oos.writeObject(serializable);
      buffer = out.getBuffer();
      buffer.putInt(sizePosition, buffer.position() - sizePosition - 4);
      return buffer;
   }

   public static Object deserialize(byte[] serializedData, int startPos, int length) throws IOException {
      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedData, startPos, length));
      try {
         return ois.readObject();
      } catch (ClassNotFoundException e) {
         throw new IllegalStateException("Unmarshalling exception", e);
      }
   }

   private static ByteBuffer grow(ByteBuffer buffer, int minCapacityIncrease) {
      ByteBuffer tmp = ByteBuffer.allocate(Math.max(buffer.capacity() << 1, buffer.capacity() + minCapacityIncrease));
      buffer.flip();
      tmp.put(buffer);
      return tmp;
   }

   private static class ByteBufferOutputStream extends OutputStream {

      private ByteBuffer buffer;

      private ByteBufferOutputStream(ByteBuffer buffer) {
         this.buffer = buffer;
      }

      private void grow(int minCapacityIncrease) {
         buffer = SerializationHelper.grow(buffer, minCapacityIncrease);
      }

      @Override
      public void write(int b) throws IOException {
         if (!buffer.hasRemaining()) {
            grow(1);
         }
         buffer.put((byte) b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
         if (buffer.remaining() < len) {
            grow(len - buffer.remaining());
         }
         buffer.put(b, off, len);
      }

      public ByteBuffer getBuffer() {
         return buffer;
      }
   }
}
