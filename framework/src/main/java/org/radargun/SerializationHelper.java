package org.radargun;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Helper class holding serialization logic.
 *
 * @author Mircea.Markus@jboss.com
 */
public class SerializationHelper {

   private static Log log = LogFactory.getLog(SerializationHelper.class);

   public static byte[] serializeObject(Serializable serializable) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(out);
      oos.writeObject(serializable);
      return out.toByteArray();
   }


   /*
    * Returns a byte array containing the two's-complement representation of the integer.<br>
	 * The byte array will be in big-endian byte-order with a fixes length of 4
	 * (the least significant byte is in the 4th element).<br>
	 * <br>
	 * <b>Example:</b><br>
	 * <code>intToByteArray(258)</code> will return { 0, 0, 1, 2 },<br>
	 * <code>BigInteger.valueOf(258).toByteArray()</code> returns { 1, 2 }.
	 * @param integer The integer to be converted.
	 * @return The byte array of length 4.
	 */
   public static byte[] intToByteArray(final int integer) {
      int byteNum = (40 - Integer.numberOfLeadingZeros(integer < 0 ? ~integer : integer)) / 8;
      byte[] byteArray = new byte[4];

      for (int n = 0; n < byteNum; n++)
         byteArray[3 - n] = (byte) (integer >>> (n * 8));

      return (byteArray);
   }


   /**
    * Convert the byte array to an int.
    *
    * @param b The byte array
    * @return The integer
    */
   public static int byteArrayToInt(byte[] b) {
      return byteArrayToInt(b, 0);
   }

   /**
    * Convert the byte array to an int starting from the given offset.
    *
    * @param b      The byte array
    * @param offset The array offset
    * @return The integer
    */
   public static int byteArrayToInt(byte[] b, int offset) {
      int value = 0;
      for (int i = 0; i < 4; i++) {
         int shift = (4 - 1 - i) * 8;
         value += (b[i + offset] & 0x000000FF) << shift;
      }
      return value;
   }

   public static void main(String[] args) {
      for (int i=0; i < Integer.MAX_VALUE; i++) {
         if (i%10000 == 0) {
            System.out.println("i = " + i);
         }
         byte[] bytes = intToByteArray(i);
         int value = byteArrayToInt(bytes);
         assert i == value;
      }
   }

   public static Object deserialize(byte[] serializedData, int startPos, int length) throws IOException {
      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedData, startPos, length));
      try {
         return ois.readObject();
      } catch (ClassNotFoundException e) {
         log.error("Unmarshalling exceptions: ", e);
         throw new IllegalStateException(e);
      }
   }

   public static byte[] prepareForSerialization(Serializable towrite) throws IOException {
      byte[] bytes = serializeObject(towrite);
      byte[] toSend = new byte[bytes.length + 4];
      byte[] size = intToByteArray(bytes.length);
      System.arraycopy(size, 0, toSend, 0, 4);
      System.arraycopy(bytes, 0, toSend, 4, bytes.length);
      return toSend;
   }
}
