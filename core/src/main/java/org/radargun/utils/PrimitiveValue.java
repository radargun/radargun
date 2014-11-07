package org.radargun.utils;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;

/**
 * Holder for primitive types. Can be used together with {@link org.radargun.utils.PrimitiveValue.ListConverter} or
 * {@link org.radargun.utils.PrimitiveValue.ObjectConverter}.
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public abstract class PrimitiveValue {

   public abstract Object getElementValue();

   @DefinitionElement(name = "string", doc = "Element representing String.")
   public static class PrimitiveString extends PrimitiveValue {

      public PrimitiveString() {
      }

      @Property(optional = false, doc = "Value")
      public String value;

      @Override
      public Object getElementValue() {
         return value;
      }
   }

   @DefinitionElement(name = "int", doc = "Element representing int.")
   public static class PrimitiveInteger extends PrimitiveValue {

      public PrimitiveInteger() {
      }

      @Property(optional = false, doc = "Value")
      public Integer value;

      @Override
      public Object getElementValue() {
         return value;
      }
   }

   @DefinitionElement(name = "long", doc = "Element representing long.")
   public static class PrimitiveLong extends PrimitiveValue {

      public PrimitiveLong() {
      }

      @Property(optional = false, doc = "Value")
      public Long value;

      @Override
      public Object getElementValue() {
         return value;
      }
   }

   @DefinitionElement(name = "boolean", doc = "Element representing boolean.")
   public static class PrimitiveBoolean extends PrimitiveValue {

      public PrimitiveBoolean() {
      }

      @Property(optional = false, doc = "Value")
      public Boolean value;

      @Override
      public Object getElementValue() {
         return value;
      }
   }

   @DefinitionElement(name = "short", doc = "Element representing short.")
   public static class PrimitiveShort extends PrimitiveValue {

      public PrimitiveShort() {
      }

      @Property(optional = false, doc = "Value")
      public Short value;

      @Override
      public Object getElementValue() {
         return value;
      }
   }

   @DefinitionElement(name = "byte", doc = "Element representing byte.")
   public static class PrimitiveByte extends PrimitiveValue {

      public PrimitiveByte() {
      }

      @Property(optional = false, doc = "Value")
      public Byte value;

      @Override
      public Object getElementValue() {
         return value;
      }
   }

   @DefinitionElement(name = "char", doc = "Element representing char.")
   public static class PrimitiveChar extends PrimitiveValue {

      public PrimitiveChar() {
      }

      @Property(optional = false, doc = "Value")
      public Character value;

      @Override
      public Object getElementValue() {
         return value;
      }
   }

   /**
    * Converter for list of objects.
    */
   public static class ListConverter extends ReflexiveConverters.ListConverter {

      public ListConverter() {
         super(new Class[] {
               PrimitiveValue.PrimitiveString.class,
               PrimitiveValue.PrimitiveInteger.class,
               PrimitiveValue.PrimitiveLong.class,
               PrimitiveValue.PrimitiveBoolean.class,
               PrimitiveValue.PrimitiveShort.class,
               PrimitiveValue.PrimitiveChar.class,
               PrimitiveValue.PrimitiveByte.class});
      }

   }

   /**
    * Converter for a single object.
    */
   public static class ObjectConverter extends ReflexiveConverters.ObjectConverter {

      public ObjectConverter() {
         super(new Class[] {
               PrimitiveValue.PrimitiveString.class,
               PrimitiveValue.PrimitiveInteger.class,
               PrimitiveValue.PrimitiveLong.class,
               PrimitiveValue.PrimitiveBoolean.class,
               PrimitiveValue.PrimitiveShort.class,
               PrimitiveValue.PrimitiveChar.class,
               PrimitiveValue.PrimitiveByte.class});
      }

   }
}
