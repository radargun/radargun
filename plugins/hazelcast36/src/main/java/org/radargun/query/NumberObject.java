package org.radargun.query;

import java.io.Serializable;

/**
 * Object to be queried containing numbers
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class NumberObject implements Serializable {
   private int integerValue;
   private double doubleValue;

   public NumberObject(int i, double d) {
      this.integerValue = i;
      this.doubleValue = d;
   }

   public int getInt() {
      return integerValue;
   }

   public double getDouble() {
      return doubleValue;
   }

   @Override
   public String toString() {
      return "NumberObject{int=" + integerValue + ", double=" + doubleValue + '}';
   }
}
