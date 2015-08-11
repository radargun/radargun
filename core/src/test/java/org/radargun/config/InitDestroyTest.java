package org.radargun.config;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Test
public class InitDestroyTest {
   public static class A {
      public ArrayList<Integer> invocations = new ArrayList<>();

      @Init
      public void init1() {
         invocations.add(1);
      }

      @Init
      public void init2() {
         invocations.add(2);
      }

      @Destroy
      public void destroy1() {
         invocations.add(-1);
      }

      @Destroy
      public void destroy2() {
         invocations.add(-2);
      }
   }

   public static class B extends A {
      @Init
      public void init3() {
         invocations.add(3);
      }

      @Destroy
      public void destroy3() {
         invocations.add(-3);
      }
   }

   public static class C extends B {
      @Override
      public void init1() {
         invocations.add(4);
      }

      @Override
      public void destroy1() {
         invocations.add(-4);
      }
   }

   public static class D {
      public ArrayList<Integer> invocations = new ArrayList<>();

      @Init
      private void init1() {
         invocations.add(1);
      }

      @Destroy
      private void destroy1() {
         invocations.add(-1);
      }
   }

   public void testB() {
      B b = new B();
      InitHelper.init(b);
      InitHelper.destroy(b);
      List<Integer> expected = Arrays.asList(1, 2, 3, -3, -2, -1);
      assertEqualsPermuted(b.invocations, expected, 0, 2);
      assertEquals(b.invocations, expected, 2, 4);
      assertEqualsPermuted(b.invocations, expected, 4, 6);
   }

   public void testC() {
      C c = new C();
      InitHelper.init(c);
      InitHelper.destroy(c);
      List<Integer> expected = Arrays.asList(4, 2, 3, -3, -2, -4);
      assertEqualsPermuted(c.invocations, expected, 0, 2);
      assertEquals(c.invocations, expected, 2, 4);
      assertEqualsPermuted(c.invocations, expected, 4, 6);
   }

   public void testD() {
      D d = new D();
      InitHelper.init(d);
      InitHelper.destroy(d);
      Assert.assertEquals(d.invocations, Arrays.asList(1, -1));
   }

   private void assertEquals(List<Integer> actual, List<Integer> expected, int fromIndex, int toIndex) {
      Assert.assertEquals((Object) actual.subList(fromIndex, toIndex), (Object) expected.subList(fromIndex, toIndex));
   }

   private void assertEqualsPermuted(List<Integer> actual, List<Integer> expected, int fromIndex, int toIndex) {
      Assert.assertEquals(new HashSet<>(actual.subList(fromIndex, toIndex)), new HashSet<>(expected.subList(fromIndex, toIndex)));
   }
}
