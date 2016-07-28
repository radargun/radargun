package org.radargun.query;

import java.io.IOException;
import java.io.Serializable;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;

/**
 * Simple object containing one string. See {@link org.radargun.stages.cache.generators.TextObjectGenerator}
 */
public class TextObject implements Serializable, PortableObject {
   private String text;

   public TextObject() {
      // for POF deserialization only
   }

   public TextObject(String text) {
      this.text = text;
   }

   // for Coherence reflection
   public String text() {
      return text;
   }

   // for generators
   public String getText() {
      return text;
   }

   @Override
   public void readExternal(PofReader pofReader) throws IOException {
      text = pofReader.readString(0);
   }

   @Override
   public void writeExternal(PofWriter pofWriter) throws IOException {
      pofWriter.writeString(0, text);
   }

   @Override
   public String toString() {
      return "TextObject{" + text + '}';
   }
}
