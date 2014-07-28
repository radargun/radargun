package org.radargun.query;

import java.io.IOException;
import java.io.Serializable;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class TextObject implements Serializable, PortableObject {
   public String text;

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
}
