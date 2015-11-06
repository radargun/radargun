package org.radargun.traits;

import java.util.HashMap;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @author Matej Cimbora
 */
@Test
public class TraitHelperTest {

   public void testInjectDefault() {
      TestStageDefault testStageDefault = new TestStageDefault();
      TestTrait testTrait = new TestTraitImpl();
      TraitHelper.InjectResult result = TraitHelper.inject(testStageDefault, new HashMap<Class<?>, Object>() {{
         put(TestTrait.class, testTrait);
      }});
      assertEquals(result, TraitHelper.InjectResult.SUCCESS);
      assertEquals(testStageDefault.testTrait, testTrait);
   }


   public void testInjectMandatory() {
      TestStageMandatory testStageMandatory = new TestStageMandatory();
      TraitHelper.InjectResult result = TraitHelper.inject(testStageMandatory, new HashMap<>());
      assertEquals(result, TraitHelper.InjectResult.FAILURE);
      assertNull(testStageMandatory.testTrait);
   }

   public void testInjectOptional() {
      TestStageOptional testStageOptional = new TestStageOptional();
      TraitHelper.InjectResult result = TraitHelper.inject(testStageOptional, new HashMap<>());
      assertEquals(result, TraitHelper.InjectResult.SUCCESS);
      assertNull(testStageOptional.testTrait);
   }

   public void testInjectSkip() {
      TestStageSkip testStageSkip = new TestStageSkip();
      TraitHelper.InjectResult result = TraitHelper.inject(testStageSkip, new HashMap<>());
      assertEquals(result, TraitHelper.InjectResult.SKIP);
      assertNull(testStageSkip.testTrait);
   }

   private static class TestStageDefault {

      @InjectTrait
      TestTrait testTrait;
   }

   private static class TestStageMandatory {

      @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
      TestTrait testTrait;
   }

   private static class TestStageOptional {

      @InjectTrait(dependency = InjectTrait.Dependency.OPTIONAL)
      TestTrait testTrait;
   }

   private static class TestStageSkip {

      @InjectTrait(dependency = InjectTrait.Dependency.SKIP)
      TestTrait testTrait;
   }

   @Trait(doc = "Test trait")
   private interface TestTrait {
   }

   private static class TestTraitImpl implements TestTrait {
   }
}
