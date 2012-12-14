package org.radargun.stages;

import org.radargun.config.Converter;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Automatically describes the stage based on the annotations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 12/12/12
 */
@Stage(doc = "")
public abstract class AbstractStage implements org.radargun.Stage {
   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(getStageName(getClass())).append(" {");
      Set<Map.Entry<String, Field>> properties = PropertyHelper.getProperties(getClass()).entrySet();

      for (Iterator<Map.Entry<String,Field>> iterator = properties.iterator(); iterator.hasNext(); ) {
         Map.Entry<String, Field> property = iterator.next();
         String propertyName = property.getKey();
         Field propertyField = property.getValue();
         sb.append(propertyName).append('=');

         propertyField.setAccessible(true);
         Object value = null;
         try {
            value = propertyField.get(this);
            Converter converter = propertyField.getAnnotation(Property.class).converter().newInstance();
            sb.append(converter.convertToString(value));
         } catch (IllegalAccessException e) {
            sb.append("<not accessible>");
         } catch (InstantiationException e) {
            sb.append("<cannot create converter: ").append(value).append(">");
         } catch (ClassCastException e) {
            sb.append("<cannot convert: ").append(value).append(">");
         } catch (Throwable t) {
            sb.append("<error ").append(t).append(": ").append(value).append(">");
         }
         if (iterator.hasNext()) {
            sb.append(", ");
         }
      }
      return sb.append(" }").toString();
   }

   public static String getStageName(Class<? extends org.radargun.Stage> clazz) {
      if (clazz == null) throw new IllegalArgumentException("Class cannot be null");
      Stage stageAnnotation = (Stage)clazz.getAnnotation(Stage.class);
      if (stageAnnotation == null) throw new IllegalArgumentException(clazz + " is not properly annotated.");
      if (!stageAnnotation.name().equals(Stage.CLASS_NAME_WITHOUT_STAGE)) return stageAnnotation.name();
      String name = clazz.getSimpleName();
      if (!name.endsWith("Stage")) throw new IllegalArgumentException(clazz.getCanonicalName());
      return name.substring(0, name.length() - 5);
   }

   public org.radargun.Stage clone() {
      try {
         return (org.radargun.Stage) super.clone();
      } catch (CloneNotSupportedException e) {
         throw new IllegalStateException();
      }
   }
}
