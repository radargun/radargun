package org.radargun.query;

import java.io.IOException;
import java.io.Serializable;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.infinispan.protostream.MessageMarshaller;

/**
 * Simple object to be queried.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Indexed
public class TextObject implements Serializable {
   @Field(index = Index.YES, analyze = Analyze.NO)
   public String text;

   public TextObject(String text) {
      this.text = text;
   }

   @Override
   public String toString() {
      return "TextObject{" + text + '}';
   }

   public static class Marshaller implements MessageMarshaller<TextObject> {
      protected static final String NAME = TextObject.class.getName();

      @Override
      public TextObject readFrom(ProtoStreamReader reader) throws IOException {
         return new TextObject(reader.readString("text"));
      }

      @Override
      public void writeTo(ProtoStreamWriter writer, TextObject textObject) throws IOException {
         writer.writeString("text", textObject.text);
      }

      @Override
      public Class<? extends TextObject> getJavaClass() {
         return TextObject.class;
      }

      @Override
      public String getTypeName() {
         return NAME;
      }
   }
}
