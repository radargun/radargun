package org.radargun.config;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.testng.annotations.Test;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Test
public class DefaultConverterTest {
   private static DefaultConverter INSTANCE = new DefaultConverter();

   public void testCollectionWhitespaces() {
      assertConverts("1, 2, 3", Arrays.asList(1, 2, 3));
      assertConverts("3,2,1", Arrays.asList(3, 2, 1));
   }

   private void assertConverts(String expression, List<Integer> expected) {
      Object value = INSTANCE.convert(expression, ParameterizedTypeImpl.make(List.class, new Type[]{Integer.class}, null));
      assertTrue(value instanceof List);
      assertEquals(((List) value).toArray(), expected.toArray());
   }

   public void testEnumSet() {
      Object value = INSTANCE.convert("TEST_1, TEST_2", ParameterizedTypeImpl.make(EnumSet.class, new Type[]{TestEnum.class}, null));
      assertTrue(value instanceof EnumSet);
      EnumSet<TestEnum> result = (EnumSet) value;
      assertEquals(result.size(), 2);
      assertTrue(result.containsAll(Arrays.asList(new TestEnum[]{TestEnum.TEST_1, TestEnum.TEST_2})));
   }

   private static enum TestEnum {
      TEST_1, TEST_2
   }
}
