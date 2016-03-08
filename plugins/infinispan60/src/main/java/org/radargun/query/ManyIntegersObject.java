package org.radargun.query;

import java.io.IOException;
import java.io.Serializable;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.NumericField;
import org.infinispan.protostream.MessageMarshaller;

/**
 * Object storing multiple numbers (used for multi-index query)
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Indexed
public class ManyIntegersObject implements Serializable {
   @NumericField
   @Field(index = Index.YES, analyze = Analyze.NO)
   private int int0;

   @NumericField
   @Field(index = Index.YES, analyze = Analyze.NO)
   private int int1;

   @NumericField
   @Field(index = Index.YES, analyze = Analyze.NO)
   private int int2;

   @NumericField
   @Field(index = Index.YES, analyze = Analyze.NO)
   private int int3;

   @NumericField
   @Field(index = Index.YES, analyze = Analyze.NO)
   private int int4;

   @NumericField
   @Field(index = Index.YES, analyze = Analyze.NO)
   private int int5;

   @NumericField
   @Field(index = Index.YES, analyze = Analyze.NO)
   private int int6;

   @NumericField
   @Field(index = Index.YES, analyze = Analyze.NO)
   private int int7;

   @NumericField
   @Field(index = Index.YES, analyze = Analyze.NO)
   private int int8;

   @NumericField
   @Field(index = Index.YES, analyze = Analyze.NO)
   private int int9;

   public ManyIntegersObject(int int0, int int1, int int2, int int3, int int4, int int5, int int6, int int7, int int8, int int9) {
      this.int0 = int0;
      this.int1 = int1;
      this.int2 = int2;
      this.int3 = int3;
      this.int4 = int4;
      this.int5 = int5;
      this.int6 = int6;
      this.int7 = int7;
      this.int8 = int8;
      this.int9 = int9;
   }

   @Override
   public String toString() {
      final StringBuilder sb = new StringBuilder("ManyIntegersObject{");
      sb.append("int0=").append(int0);
      sb.append(", int1=").append(int1);
      sb.append(", int2=").append(int2);
      sb.append(", int3=").append(int3);
      sb.append(", int4=").append(int4);
      sb.append(", int5=").append(int5);
      sb.append(", int6=").append(int6);
      sb.append(", int7=").append(int7);
      sb.append(", int8=").append(int8);
      sb.append(", int9=").append(int9);
      sb.append('}');
      return sb.toString();
   }

   public static class Marshaller implements MessageMarshaller<ManyIntegersObject> {
      protected static final String NAME = ManyIntegersObject.class.getName();

      @Override
      public ManyIntegersObject readFrom(ProtoStreamReader reader) throws IOException {
         return new ManyIntegersObject(
            reader.readInt("int0"),
            reader.readInt("int1"),
            reader.readInt("int2"),
            reader.readInt("int3"),
            reader.readInt("int4"),
            reader.readInt("int5"),
            reader.readInt("int6"),
            reader.readInt("int7"),
            reader.readInt("int8"),
            reader.readInt("int9"));
      }

      @Override
      public void writeTo(ProtoStreamWriter writer, ManyIntegersObject numberObject) throws IOException {
         writer.writeInt("int0", numberObject.int0);
         writer.writeInt("int1", numberObject.int1);
         writer.writeInt("int2", numberObject.int2);
         writer.writeInt("int3", numberObject.int3);
         writer.writeInt("int4", numberObject.int4);
         writer.writeInt("int5", numberObject.int5);
         writer.writeInt("int6", numberObject.int6);
         writer.writeInt("int7", numberObject.int7);
         writer.writeInt("int8", numberObject.int8);
         writer.writeInt("int9", numberObject.int9);
      }

      @Override
      public Class<? extends ManyIntegersObject> getJavaClass() {
         return ManyIntegersObject.class;
      }

      @Override
      public String getTypeName() {
         return NAME;
      }
   }
}
