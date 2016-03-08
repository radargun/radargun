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
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public final class SerializationHelper {
   private static final int MIN_REMAINING = 32;

   private SerializationHelper() {}

   /**
    * Write serialized representation of given object to the buffer on the current position.
    * If the object does not fit to buffer's capacity, a new buffer is allocated, old buffer
    * is copied into the new buffer and then the object is written into the new buffer.
    *
    * Note than the original buffer can be modified even if the object does not fit there.
    *
    * @param serializable
    * @param buffer Buffer with appended serialized representation of the object.
    * @return
    * @throws IOException
    */
   public static ByteBuffer serializeObject(Serializable serializable, ByteBuffer buffer) throws IOException {
      try (ByteBufferOutputStream out = new ByteBufferOutputStream(buffer); ObjectOutputStream oos = new ObjectOutputStream(out)) {
         oos.writeObject(serializable);
         return out.getBuffer();
      }
   }

   /**
    * Write length of serialized representation of given object and the serialized representation
    * to the buffer on the current position. The length is represented as 4 byte integer in current byte order.
    * If the object does not fit to buffer's capacity, a new buffer is allocated, old buffer
    * is copied into the new buffer and then the object is written into the new buffer.
    *
    * Note than the original buffer can be modified even if the object does not fit there.
    *
    * @param serializable
    * @param buffer Buffer with appended serialized representation of the object.
    * @return
    * @throws IOException
    */
   public static ByteBuffer serializeObjectWithLength(Serializable serializable, ByteBuffer buffer) throws IOException {
      if (buffer.remaining() < MIN_REMAINING) {
         buffer = grow(buffer, MIN_REMAINING);
      }
      int sizePosition = buffer.position();
      buffer.position(sizePosition + 4);
      try (ByteBufferOutputStream out = new ByteBufferOutputStream(buffer); ObjectOutputStream oos = new ObjectOutputStream(out)) {
         oos.writeObject(serializable);
         buffer = out.getBuffer();
         buffer.putInt(sizePosition, buffer.position() - sizePosition - 4);
         return buffer;
      }
   }

   /**
    * Append long to the end of the buffer, possibly reallocating it.
    *
    * @param value
    * @param buffer
    * @return
    */
   public static ByteBuffer appendLong(long value, ByteBuffer buffer) {
      if (buffer.remaining() < 8) buffer = grow(buffer, MIN_REMAINING);
      buffer.putLong(value);
      return buffer;
   }

   /**
    * Deserialize object from given byte array, starting at startPos and using length bytes.
    *
    * @param serializedData
    * @param startPos
    * @param length
    * @return
    * @throws IOException
    */
   public static Object deserialize(byte[] serializedData, int startPos, int length) throws IOException {
      try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedData, startPos, length))) {
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
