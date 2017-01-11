package org.radargun.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Definition for more complex arguments:
 *
 * <my-stage>
 *    <my-property another-attribute="xxx">yyy</my-property>
 * </my-stage>
 *
 * That should be also equivalent to:
 *
 * <my-stage>
 *    <my-property>
 *       <another-attribute>xxx</another-attribute>
 *       yyy
 *    </my-property>
 * </my-stage>
 *
 * Definitions can even nest:
 *
 * <my-stage>
 *    <my-property>
 *       <foo bar="baz">goo</foo>
 *       <moo>oom</moo>
 *    </my-property>
 * </my-stage>
 */
public class ComplexDefinition implements Definition {
   private List<Entry> attributes;
   private String namespace;

   /**
    * Definition can use multiple entries with same name - {@link ComplexConverter} should handle that.
    * @return
    */
   public List<Entry> getAttributes() {
      return attributes == null ? Collections.EMPTY_LIST : Collections.unmodifiableList(attributes);
   }

   /**
    * When using {@link PropertyHelper#setPropertiesFromDefinitions(Object, java.util.Map, boolean, boolean)}
    * we don't allow duplicate entry names.
    * Due to the way templates are merged, we just overwrite the value in the map by the last entry.
    * @return
    */
   public Map<String, Definition> getAttributeMap() {
      if (attributes == null) return Collections.EMPTY_MAP;
      Map<String, Definition> map = new HashMap<String, Definition>();
      for (Entry entry : attributes) {
         map.put(entry.name, entry.definition);
      }
      return map;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      if (attributes != null) {
         boolean first = true;
         for (Entry entry : attributes) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(entry.name.isEmpty() ? "<value>" : entry.name).append("=");
            if (entry.definition instanceof SimpleDefinition) {
               sb.append(entry.definition);
            } else {
               sb.append('[').append(entry.definition).append(']');
            }
         }
      }
      return sb.toString();
   }

   public void add(String attribute, Definition definition) {
      if (attributes == null) attributes = new ArrayList<Entry>();
      attributes.add(new Entry(attribute, definition));
   }

   public void setNamespace(String namespace) {
      this.namespace = namespace;
   }

   public String getNamespace() {
      return namespace;
   }

   @Override
   public Definition apply(Definition other) {
      if (other instanceof ComplexDefinition) {
         ComplexDefinition cd = (ComplexDefinition) other;
         if (!Objects.equals(namespace, cd.namespace)) {
            throw new IllegalArgumentException("Namespace for overwritten definition does not match: " + namespace + " vs. " + cd.namespace);
         }
         ComplexDefinition nd = new ComplexDefinition();
         nd.namespace = this.namespace;
         nd.attributes = new ArrayList<>(this.attributes);
         nd.attributes.addAll(cd.attributes);
         return nd;
      } else {
         return other;
      }
   }

   public static class Entry implements Serializable {
      public final String name;
      public final Definition definition;

      public Entry(String name, Definition definition) {
         this.name = name;
         this.definition = definition;
      }
   }
}
