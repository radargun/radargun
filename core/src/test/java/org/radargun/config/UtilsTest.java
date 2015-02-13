package org.radargun.config;

import org.radargun.utils.Utils;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
@Test
public class UtilsTest {

   public void testFindThrowableCauseByClass() {
      Exception root = new TestRootException();
      Exception child = new TestChildException(root);
      Exception result = Utils.findThrowableCauseByClass(child, TestRootException.class);
      assertTrue(result instanceof TestRootException);

      child = new TestChildException();
      result = Utils.findThrowableCauseByClass(child, TestRootException.class);
      assertEquals(null, result);
   }

   @Test(expectedExceptions = IllegalArgumentException.class)
   public void testFindThrowableCauseByClassNullArg() {
      Utils.findThrowableCauseByClass(new TestChildException(), null);
   }

   private static class TestRootException extends Exception {}

   private static class TestChildException extends Exception {

      private TestChildException() {
      }

      private TestChildException(Throwable cause) {
         super(cause);
      }
   }
}
