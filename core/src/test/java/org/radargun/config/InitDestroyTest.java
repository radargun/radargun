package org.radargun.config;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.testng.Assert.*;

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

   public static class D extends B {
      @Init
      @Override
      public void init1() {
         invocations.add(4);
      }

      @Destroy
      @Override
      public void destroy1() {
         invocations.add(-4);
      }
   }

   public static class E {
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
      List<Integer> expected = Arrays.asList(1, 2, 3);
      InitHelper.init(b);
      assertEquals(b.invocations.size(), 3);
      assertListEqualsPermuted(b.invocations, expected, 0, 3);
      assertListIndexEquals(b.invocations, expected, 2);

      expected = Arrays.asList(-3, -2, -1);
      b.invocations.clear();
      InitHelper.destroy(b);
      assertEquals(b.invocations.size(), 3);
      assertListIndexEquals(b.invocations, expected, 0);
      assertListEqualsPermuted(b.invocations, expected, 1, 3);
   }

   public void testC() {
      C c = new C();
      List<Integer> expected = Arrays.asList(4, 2, 3);
      InitHelper.init(c);
      assertEquals(c.invocations.size(), 3);
      assertListEqualsPermuted(c.invocations, expected, 0, 2);
      assertListIndexEquals(c.invocations, expected, 2);

      expected = Arrays.asList(-3, -2, -4);
      c.invocations.clear();
      InitHelper.destroy(c);
      assertEquals(c.invocations.size(), 3);
      assertListIndexEquals(c.invocations, expected, 0);
      assertListEqualsPermuted(c.invocations, expected, 1, 3);
   }

   public void testD() {
      D e = new D();
      List<Integer> expected = Arrays.asList(2, 3, 4);
      InitHelper.init(e);
      assertEquals(e.invocations.size(), 3);
      assertEquals(e.invocations, expected);

      expected = Arrays.asList(-4, -3, -2);
      e.invocations.clear();
      InitHelper.destroy(e);
      assertEquals(e.invocations.size(), 3);
      assertEquals(e.invocations, expected);
   }

   public void testE() {
      E d = new E();
      InitHelper.init(d);
      InitHelper.destroy(d);
      Assert.assertEquals(d.invocations, Arrays.asList(1, -1));
   }

   private void assertListIndexEquals(List<Integer> actual, List<Integer> expected, int index) {
      assertEquals(actual.get(index), expected.get(index));
   }

   private void assertListEqualsPermuted(List<Integer> actual, List<Integer> expected, int fromIndex, int toIndex) {
      Assert.assertEquals(new HashSet<>(actual.subList(fromIndex, toIndex)), new HashSet<>(expected.subList(fromIndex, toIndex)));
   }
}
