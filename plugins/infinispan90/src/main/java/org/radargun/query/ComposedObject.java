package org.radargun.query;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.infinispan.protostream.MessageMarshaller;

/**
 * @author Matej Cimbora
 */
@Indexed
public class ComposedObject implements Serializable {

   @IndexedEmbedded
   private TextObject textObject;
   @IndexedEmbedded
   private NumberObject numberObject;
   @IndexedEmbedded
   private List<TextObject> textObjectList;
   @IndexedEmbedded
   private List<NumberObject> numberObjectList;

   public ComposedObject(TextObject textObject, NumberObject numberObject, List<TextObject> textObjectList, List<NumberObject> numberObjectList) {
      this.textObject = textObject;
      this.numberObject = numberObject;
      this.textObjectList = textObjectList;
      this.numberObjectList = numberObjectList;
   }

   public TextObject getTextObject() {
      return textObject;
   }

   public NumberObject getNumberObject() {
      return numberObject;
   }

   public List<TextObject> getTextObjectList() {
      return textObjectList;
   }

   public List<NumberObject> getNumberObjectList() {
      return numberObjectList;
   }

   @Override
   public String toString() {
      return "ComposedObject{" +
         "textObject=" + textObject +
         ", numberObject=" + numberObject +
         ", textObjectList=" + textObjectList +
         ", numberObjectList=" + numberObjectList +
         '}';
   }

   public static class Marshaller implements MessageMarshaller<ComposedObject> {

      private static final String NAME = ComposedObject.class.getName();

      @Override
      public ComposedObject readFrom(ProtoStreamReader reader) throws IOException {
         return new ComposedObject(reader.readObject("textObject", TextObject.class),
            reader.readObject("numberObject", NumberObject.class),
            reader.readCollection("textObjectList", new ArrayList<TextObject>(), TextObject.class),
            reader.readCollection("numberObjectList", new ArrayList<NumberObject>(), NumberObject.class));
      }

      @Override
      public void writeTo(ProtoStreamWriter writer, ComposedObject composedObject) throws IOException {
         writer.writeObject("textObject", composedObject.getTextObject(), TextObject.class);
         writer.writeObject("numberObject", composedObject.getNumberObject(), NumberObject.class);
         writer.writeCollection("textObjectList", composedObject.getTextObjectList(), TextObject.class);
         writer.writeCollection("numberObjectList", composedObject.getNumberObjectList(), NumberObject.class);
      }

      @Override
      public Class<? extends ComposedObject> getJavaClass() {
         return ComposedObject.class;
      }

      @Override
      public String getTypeName() {
         return NAME;
      }
   }
}
