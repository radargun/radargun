package org.radargun.config;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Test
public class EvaluatorTest {
   public void testWithDefaultNoReplacement() {
      assertEvals("${custom.property:aDefaultVal}", "aDefaultVal");
   }

   public void testWithDefaultAndReplacement() {
      System.setProperty("org.radargun.testWithDefaultAndReplacement", "nonDefaultValue");
      assertEvals("${org.radargun.testWithDefaultAndReplacement:aDefaultVal}", "nonDefaultValue");
   }

   @Test(expectedExceptions = IllegalArgumentException.class)
   public void testFailureOnNonDefault() {
      Evaluator.parseString("${org.radargun.testFailureOnNonDefault}");
   }

   public void testNoDefaultAndExisting() {
      System.setProperty("org.radargun.testNoDefaultAndExisting", "nonDefaultValue");
      assertEvals("${org.radargun.testNoDefaultAndExisting}", "nonDefaultValue");
   }

   public void testConcatWithVar() {
      System.setProperty("org.radargun.testConcatWithVar", "foo");
      assertEvals("${org.radargun.testConcatWithVar}yyy", "fooyyy");
      assertEvals("xxx${org.radargun.testConcatWithVar}", "xxxfoo");
      assertEvals("xxx${org.radargun.testConcatWithVar}yyy", "xxxfooyyy");
   }

   public void testSimpleExpression() {
      assertEvals("#{1 + 2 * 3 - 1}", "6");
      assertEvals("#{ (1+2 )*3}", "9");
      assertEvals("#{ 9 / 3 + 1}", "4");
      assertEvals("#{7 % 2 + 1}", "2");
      assertEvals("#{2,5..8,10}", "2, 5, 6, 7, 8, 10");
   }

   public void testConcatWithExpression() {
      assertEvals("xxx#{1 + 2}yyy", "xxx3yyy");
      assertEvals("#{1 + 2}yyy", "3yyy");
      assertEvals("xxx#{1 + 2}", "xxx3");
   }

   public void testExpressionWithVar() {
      System.setProperty("org.radargun.testExpressionWithVar", "2");
      assertEvals("#{ 1 + ${ org.radargun.testExpressionWithVar } * ${org.radargun.noProperty: 3}}", "7");
   }

   public void testColons() {
      System.setProperty("org.radargun.testColons", "xxx");
      assertEvals("fo:o${org.radargun.testColons}bar:${org.radargun.testColons:yyy}bar:${org.radargun.noProperty:zzz}bar:foo",
         "fo:oxxxbar:xxxbar:zzzbar:foo");
   }

   public void testNegative() {
      System.setProperty("org.radargun.testNegative", "2");
      assertEvals("#{ -1 }", "-1");
      assertEvals("#{ - 2 }", "-2");
      assertEvals("#{ 0 - 3 }", "-3");
      assertEvals("#{ 5+ -4 }", "1");
      assertEvals("#{ 6 + (-5) }", "1");
      assertEvals("#{${org.radargun.testNegative} - 1}", "1");
   }

   public void testMinMax() {
      assertEvals("#{ max(1, 2, 3) }", "3");
      assertEvals("#{ min 2, 1, 3 }", "1");
      // warning should be emitted
      assertEvals("#{ max 2 }", "2");
   }

   public void testFunctions() {
      assertEvals("#{ ceil(0.6) }", "1");
      assertEvals("#{ ceil(double(1) / 2) }", "1");
      assertEvals("#{ floor 42.1 }", "42");
      assertEvals("#{ abs(-123) }", "123");
      assertEvals("#{ 5 * abs(5) }", "25");
      assertEvals("#{ 6 * abs(-6.5) }", "39.0");
      assertEvals("#{ gcd (6, 21, 27) }", "3");
   }

   public void testListGetBasic() {
      assertEvals("#{ (3).get(0) }", "3");
      assertEvals("#{ (1,2,3).get(1) }", "2");
      assertEvals("#{ (abc,def,ghi).get(0) }", "abc");

      System.setProperty("org.radargun.testListGet", "ghi");
      assertEvals("#{ (abc,def,${org.radargun.testListGet}).get(0) }", "abc");
   }

   public void testListGetComplex() {
      assertEvals("#{ (3).get(0) + 2 }", "5");
      assertEvals("#{ (1,2,3).get(1) + 2 * 2}", "6");
      assertEvals("#{ (1,2,3).get(1) - 2 * 2}", "-2");
      assertEvals("#{ (abc,def,ghi).get(0),3 }", "abc, 3");

      System.setProperty("org.radargun.testListGetSize", "1, 2, 3, 4");
      System.setProperty("org.radargun.testListGetSize.counter", "1");
      System.setProperty("org.radargun.testListGet", "ghi");
      assertEvals("#{ ${org.radargun.testListGet}.get(0) }", "ghi");
      assertEvals("#{ (abc,def,${org.radargun.testListGet}).get(0),cde }", "abc, cde");
      assertEvals("#{${org.radargun.testListGetSize}.get(${org.radargun.testListGetSize.counter})}", "2");
   }

   public void testListSize() {
      System.setProperty("org.radargun.testListSize", "1, 2, 3, 4");
      assertEvals("#{(1,2,3).size}", "3");
      assertEvals("#{(1).size}", "1");
      assertEvals("#{(abc,def).size}", "2");
      assertEvals("#{(abc,def).size}", "2");
      assertEvals("#{${org.radargun.testListSize}.size}", "4");
   }

   private static void assertEvals(String expression, String expected) {
      assertEquals(Evaluator.parseString(expression), expected);
   }
}
