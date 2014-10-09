package org.radargun.config;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Type;
import java.util.Arrays;
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
      Object value = INSTANCE.convert(expression, ParameterizedTypeImpl.make(List.class, new Type[] { Integer.class }, null));
      assertTrue(value instanceof List);
      assertEquals(((List) value).toArray(), expected.toArray());
   }
}
