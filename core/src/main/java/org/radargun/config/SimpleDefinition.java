package org.radargun.config;

/**
 * This is the classical definition for defining properties as attributes:
 *
 * <my-stage my-property="simple definition"/>
 *
 * or using simple nested attribute:
 *
 * <my-stage>
 *    <my-property>simple definition</my-property>
 * </my-stage>
 *
 * As soon as you want to add another attribute, {@link ComplexDefinition} has to be used.
 */
public class SimpleDefinition implements Definition {
   public final String value;
   public final Source source;

   public enum Source {
      ATTRIBUTE,
      TEXT,
   }

   public SimpleDefinition(String value, Source source) {
      this.value = value;
      this.source = source;
   }

   @Override
   public Definition apply(Definition other) {
      return other;
   }

   @Override
   public String toString() {
      return value;
   }
}
