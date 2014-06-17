package org.radargun.query;

import org.radargun.stages.cache.generators.ValueGenerator;

/**
 * Generates text objects
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class TextObjectGenerator implements ValueGenerator {
   @Override
   public int sizeOf(Object value) {
      return ((TextObject) value).text.length();
   }

   @Override
   public boolean checkValue(Object value, int expectedSize) {
      TextObject textObject = (TextObject) value;
      return textObject.text != null && textObject.text.length() == expectedSize;
   }
}
