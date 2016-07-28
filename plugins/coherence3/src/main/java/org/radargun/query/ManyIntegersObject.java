package org.radargun.query;

import java.io.IOException;
import java.io.Serializable;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;

/**
 * Object storing multiple numbers (used for multi-index query)
 */
public class ManyIntegersObject implements Serializable, PortableObject {
   private int int0;
   private int int1;
   private int int2;
   private int int3;
   private int int4;
   private int int5;
   private int int6;
   private int int7;
   private int int8;
   private int int9;

   public ManyIntegersObject() {
   }

   public ManyIntegersObject(int int0, int int1, int int2, int int3, int int4, int int5, int int6, int int7, int int8, int int9) {
      this.int0 = int0;
      this.int1 = int1;
      this.int2 = int2;
      this.int3 = int3;
      this.int4 = int4;
      this.int5 = int5;
      this.int6 = int6;
      this.int7 = int7;
      this.int8 = int8;
      this.int9 = int9;
   }

   public int int0() {
      return int0;
   }

   public int int1() {
      return int1;
   }

   public int int2() {
      return int2;
   }

   public int int3() {
      return int3;
   }

   public int int4() {
      return int4;
   }

   public int int5() {
      return int5;
   }

   public int int6() {
      return int6;
   }

   public int int7() {
      return int7;
   }

   public int int8() {
      return int8;
   }

   public int int9() {
      return int9;
   }

   @Override
   public String toString() {
      final StringBuilder sb = new StringBuilder("ManyIntegersObject{");
      sb.append("int0=").append(int0);
      sb.append(", int1=").append(int1);
      sb.append(", int2=").append(int2);
      sb.append(", int3=").append(int3);
      sb.append(", int4=").append(int4);
      sb.append(", int5=").append(int5);
      sb.append(", int6=").append(int6);
      sb.append(", int7=").append(int7);
      sb.append(", int8=").append(int8);
      sb.append(", int9=").append(int9);
      sb.append('}');
      return sb.toString();
   }


   @Override
   public void readExternal(PofReader pofReader) throws IOException {
      int0 = pofReader.readInt(0);
      int1 = pofReader.readInt(1);
      int2 = pofReader.readInt(2);
      int3 = pofReader.readInt(3);
      int4 = pofReader.readInt(4);
      int5 = pofReader.readInt(5);
      int6 = pofReader.readInt(6);
      int7 = pofReader.readInt(7);
      int8 = pofReader.readInt(8);
      int9 = pofReader.readInt(9);
   }

   @Override
   public void writeExternal(PofWriter pofWriter) throws IOException {
      pofWriter.writeInt(0, int0);
      pofWriter.writeInt(1, int1);
      pofWriter.writeInt(2, int2);
      pofWriter.writeInt(3, int3);
      pofWriter.writeInt(4, int4);
      pofWriter.writeInt(5, int5);
      pofWriter.writeInt(6, int6);
      pofWriter.writeInt(7, int7);
      pofWriter.writeInt(8, int8);
      pofWriter.writeInt(9, int9);
   }
}
