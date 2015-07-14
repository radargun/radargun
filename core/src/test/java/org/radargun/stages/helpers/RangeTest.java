package org.radargun.stages.helpers;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author Matej Cimbora
 */
@Test
public class RangeTest {

   public void testDivide() {
      Range range = Range.divideRange(10, 2, 0);
      assertEquals(range.getStart(), 0);
      assertEquals(range.getEnd(), 5);

      range = Range.divideRange(10, 2, 1);
      assertEquals(range.getStart(), 5);
      assertEquals(range.getEnd(), 10);

      range = Range.divideRange(10, 5, 1);
      assertEquals(range.getSize(), 2);

      range = Range.divideRange(1, 1, 0);
      assertEquals(range.getSize(), 1);

      range = Range.divideRange(1, 1, 1);
      assertEquals(range.getSize(), 1);

      range = Range.divideRange(0, 1, 1);
      assertEquals(range.getSize(), 0);
   }

   public void testShift() {
      Range range = new Range(0, 10);
      range = range.shift(5);
      assertEquals(range.getStart(), 5);
      assertEquals(range.getEnd(), 15);

      range = new Range(0, 10);
      range = range.shift(-10);
      assertEquals(range.getStart(), -10);
      assertEquals(range.getEnd(), 0);
      assertEquals(range.getSize(), 10);
   }
}
