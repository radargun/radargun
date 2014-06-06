package org.radargun.config;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

/**
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Test
public class EvaluatorTest {
   public void testWithDefaultNoReplacement() {
      assertEquals("aDefaultVal", Evaluator.parseString("${custom.property:aDefaultVal}"));
   }

   public void testWithDefaultAndReplacement(){
      System.setProperty("org.radargun.testWithDefaultAndReplacement", "nonDefaultValue");
      assertEquals("nonDefaultValue", Evaluator.parseString("${org.radargun.testWithDefaultAndReplacement:aDefaultVal}"));
   }

   @Test(expectedExceptions = IllegalArgumentException.class)
   public void testFailureOnNonDefault() {
      Evaluator.parseString("${org.radargun.testFailureOnNonDefault}");
   }

   public void testNoDefaultAndExisting() {
      System.setProperty("org.radargun.testNoDefaultAndExisting", "nonDefaultValue");
      assertEquals("nonDefaultValue", Evaluator.parseString("${org.radargun.testNoDefaultAndExisting}"));
   }

   public void testConcatWithVar() {
      System.setProperty("org.radargun.testConcatWithVar", "foo");
      assertEquals("fooyyy", Evaluator.parseString("${org.radargun.testConcatWithVar}yyy"));
      assertEquals("xxxfoo", Evaluator.parseString("xxx${org.radargun.testConcatWithVar}"));
      assertEquals("xxxfooyyy", Evaluator.parseString("xxx${org.radargun.testConcatWithVar}yyy"));
   }

   public void testSimpleExpression() {
      assertEquals("6", Evaluator.parseString("#{1 + 2 * 3 - 1}"));
      assertEquals("9", Evaluator.parseString("#{ (1+2 )*3}"));
      assertEquals("4", Evaluator.parseString("#{ 9 / 3 + 1}"));
      assertEquals("2", Evaluator.parseString("#{7 % 2 + 1}"));
      assertEquals("2,5,6,7,8,10", Evaluator.parseString("#{2,5..8,10}"));
   }

   public void testConcatWithExpression() {
      assertEquals("xxx3yyy", Evaluator.parseString("xxx#{1 + 2}yyy"));
      assertEquals("3yyy", Evaluator.parseString("#{1 + 2}yyy"));
      assertEquals("xxx3", Evaluator.parseString("xxx#{1 + 2}"));
   }

   public void testExpressionWithVar() {
      System.setProperty("org.radargun.testExpressionWithVar", "2");
      assertEquals("7", Evaluator.parseString("#{ 1 + ${ org.radargun.testExpressionWithVar } * ${org.radargun.noProperty: 3}}"));
   }

   public void testColons() {
      System.setProperty("org.radargun.testColons", "xxx");
      assertEquals("fo:oxxxbar:xxxbar:zzzbar:foo",
            Evaluator.parseString("fo:o${org.radargun.testColons}bar:${org.radargun.testColons:yyy}bar:${org.radargun.noProperty:zzz}bar:foo"));
   }
}
