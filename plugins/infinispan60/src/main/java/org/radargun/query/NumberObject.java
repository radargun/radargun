package org.radargun.query;

import java.io.IOException;
import java.io.Serializable;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.infinispan.protostream.MessageMarshaller;

/**
 * Object to be queried containing numbers
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Indexed
public class NumberObject implements Serializable {
   @Field(index = Index.YES, analyze = Analyze.NO)
   public int integerValue;

   @Field(index = Index.YES, analyze = Analyze.NO)
   public double doubleValue;

   public NumberObject(int i, double d) {
      this.integerValue = i;
      this.doubleValue = d;
   }

   public int getInt() {
      return integerValue;
   }

   public double getDouble() {
      return doubleValue;
   }

   @Override
   public String toString() {
      return "NumberObject{int=" + integerValue + ", double=" + doubleValue + '}';
   }

   public static class Marshaller implements MessageMarshaller<NumberObject> {
      protected static final String NAME = NumberObject.class.getName();

      @Override
      public NumberObject readFrom(ProtoStreamReader reader) throws IOException {
         return new NumberObject(reader.readInt("integerValue"), reader.readDouble("doubleValue"));
      }

      @Override
      public void writeTo(ProtoStreamWriter writer, NumberObject numberObject) throws IOException {
         writer.writeInt("integerValue", numberObject.integerValue);
         writer.writeDouble("doubleValue", numberObject.doubleValue);
      }

      @Override
      public Class<? extends NumberObject> getJavaClass() {
         return NumberObject.class;
      }

      @Override
      public String getTypeName() {
         return NAME;
      }
   }
}
