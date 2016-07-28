package org.radargun.query;

import java.io.Serializable;

/**
 * Object storing multiple numbers (used for multi-index query)
 */
public class ManyIntegersObject implements Serializable {
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
}
