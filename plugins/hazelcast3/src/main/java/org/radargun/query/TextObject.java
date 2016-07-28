package org.radargun.query;

import java.io.Serializable;

/**
 * Simple object containing one string. See {@link org.radargun.stages.cache.generators.TextObjectGenerator}
 */
public class TextObject implements Serializable {
   private String text;

   public TextObject(String text) {
      this.text = text;
   }

   public String getText() {
      return text;
   }

   @Override
   public String toString() {
      return "TextObject{" + text + '}';
   }
}
