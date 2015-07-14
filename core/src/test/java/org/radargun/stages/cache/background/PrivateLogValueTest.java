package org.radargun.stages.cache.background;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author Matej Cimbora
 */
@Test
public class PrivateLogValueTest {

   public void testBasicOperations() {
      PrivateLogValue value = new PrivateLogValue(0, 0);
      assertEquals(value.getThreadId(), 0);
      assertEquals(value.getOperationId(0), 0);
      // array index out of bounds
      // assertEquals(value.getOperationId(1), 0);

      assertTrue(value.contains(0));
      assertFalse(value.contains(1));

      assertTrue(value.size() == 1);

      assertFalse(value.equals(new PrivateLogValue(0, 1)));
      assertEquals(value, new PrivateLogValue(0, 0));
   }

   public void testWith() {
      PrivateLogValue value = new PrivateLogValue(0, 0);
      value = value.with(1);
      assertEquals(value.getOperationId(0), 0);
      assertEquals(value.getOperationId(1), 1);
      value = value.with(2);
      assertEquals(value.getOperationId(2), 2);
   }

   public void testShift() {
      PrivateLogValue value = new PrivateLogValue(0, 0);
      value = value.shift(0, 1);
      assertEquals(value.getOperationId(0), 0);
      assertEquals(value.getOperationId(1), 1);
      value = value.shift(2, 3);
      assertEquals(value.size(), 1);
      assertEquals(value.getOperationId(0), 3);
      value = value.with(1);
      value = value.with(2);
      assertEquals(value.size(), 3);
      value = value.shift(2, 4);
      assertEquals(value.size(), 2);
   }
}
