package org.radargun.utils;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;

/**
 * @author Matej Cimbora
 */
@DefinitionElement(name = "property", doc = "Holder for key-value pairs.")
public class KeyValueProperty {

   @Property(doc = "String key.")
   private String key;
   @Property(doc = "String value.")
   private String value;

   public String getKey() {
      return key;
   }

   public void setKey(String key) {
      this.key = key;
   }

   public String getValue() {
      return value;
   }

   public void setValue(String value) {
      this.value = value;
   }

   @Override
   public String toString() {
      return "KeyValueProperty{" +
            "key='" + key + '\'' +
            ", value='" + value + '\'' +
            '}';
   }

   public static class KeyValuePairListConverter extends ReflexiveConverters.ListConverter {

      public KeyValuePairListConverter() {
         super(new Class[] {KeyValueProperty.class});
      }
   }
}
