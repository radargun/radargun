package org.radargun.stages.helpers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class Range {

   private long start;
   private long end;

   private Range() {}

   public Range(long start, long end) {
      this.start = start;
      this.end = end;
   }

   public long getStart() {
      return start;
   }

   public long getEnd() {
      return end;
   }

   public long getSize() {
      return end - start;
   }

   public Range shift(long shift) {
      return new Range(start + shift, end + shift);
   }

   @Override
   public int hashCode() {
      return (int) (start + 31 * (end - 1));
   }

   @Override
   public boolean equals(Object other) {
      if (other == null || !(other instanceof Range)) return false;
      Range otherRange = (Range) other;
      return start == otherRange.getStart() && end == otherRange.getEnd();
   }

   @Override
   public String toString() {
      return "[" + start + ", " + end + "]";
   }

   /**
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
   public static Range divideRange(long numKeys, int numParts, int partIdx) {
      long base = (numKeys / numParts) + 1;
      long mod = numKeys % numParts;
      if (partIdx < mod) {
         long startKey = partIdx * base;
         return new Range(startKey, startKey + base);
      } else {
         long startKey = base * mod + (partIdx - mod) * (base - 1);
         return new Range(startKey, startKey + base - 1);
      }
   }

   public static List<List<Range>> balance(List<Range> ranges, int numParts) {
      int totalSize = 0;
      for (Range range : ranges) {
         totalSize += range.getSize();
      }
      Iterator<Range> iterator = ranges.iterator();
      Range currentRange;
      if (iterator.hasNext()) {
         currentRange = iterator.next();
      } else {
         currentRange = new Range(0, 0);
      }

      List<List<Range>> balanced = new ArrayList<List<Range>>(numParts);

      for (int i = 0; i < numParts; ++i) {
         long myRangeSize = divideRange(totalSize, numParts, i).getSize();
         List<Range> myRanges = new ArrayList<Range>();

         while (currentRange.getSize() <= myRangeSize) {
            myRanges.add(currentRange);
            myRangeSize -= currentRange.getSize();
            if (iterator.hasNext()) {
               currentRange = iterator.next();
            } else {
               break; // this should happen only for the last range
            }
         }
         if (myRangeSize != 0) {
            myRanges.add(new Range(currentRange.getStart(), currentRange.getStart() + myRangeSize));
            currentRange = new Range(currentRange.getStart() + myRangeSize, currentRange.getEnd());
         }
         balanced.add(myRanges);
      }
      return balanced;
   }
}
