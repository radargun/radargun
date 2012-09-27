/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stages.helpers;

public class RangeHelper {

   private RangeHelper() {}
   
   public static class Range {
      private int start;
      private int end;
      
      public Range(int start, int end) {
         super();
         this.start = start;
         this.end = end;
      }
      
      public int getStart() {
         return start;
      }
      public int getEnd() {
         return end;
      }
      public int getSize() {
         return end - start;
      }
   }
   
   /**
    * 
    * Returns pair [startKey, endKey] that specifies a subrange { startKey, ..., endKey-1 } of key
    * range { 0, 1, ..., numKeys-1 } divideRange divides the keyset evenly to numParts parts with
    * difference of part lengths being max 1.
    * 
    * @param numKeys
    *           Total number of keys
    * @param numParts
    *           Number of parts we're dividing to
    * @param partIdx
    *           Index of part we want to get
    * @return The pair [startKey, endKey]
    */
   public static Range divideRange(int numKeys, int numParts, int partIdx) {
      int base = (numKeys / numParts) + 1;
      int mod = numKeys % numParts;
      if (partIdx < mod) {
         int startKey = partIdx * base;
         return new Range(startKey, startKey + base);
      } else {
         int startKey = base * mod + (partIdx - mod) * (base - 1);
         return new Range(startKey, startKey + base - 1);
      }
   }
}
