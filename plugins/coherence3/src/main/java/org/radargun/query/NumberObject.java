package org.radargun.query;

import java.io.IOException;
import java.io.Serializable;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;

/**
 * Object to be queried containing numbers
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class NumberObject implements Serializable, PortableObject {
   private int integerValue;
   private double doubleValue;

   public NumberObject() {
      // for POF deserialization only
   }

   public NumberObject(int i, double d) {
      this.integerValue = i;
      this.doubleValue = d;
   }

   public int integerValue() {
      return integerValue;
   }

   public double doubleValue() {
      return doubleValue;
   }

   public int getInt() {
      return integerValue;
   }

   public double getDouble() {
      return doubleValue;
   }

   @Override
   public void readExternal(PofReader pofReader) throws IOException {
      integerValue = pofReader.readInt(0);
      doubleValue = pofReader.readDouble(1);
   }

   @Override
   public void writeExternal(PofWriter pofWriter) throws IOException {
      pofWriter.writeInt(0, integerValue);
      pofWriter.writeDouble(1, doubleValue);
   }
}
