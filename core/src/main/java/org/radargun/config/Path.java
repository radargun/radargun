package org.radargun.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Location of @Property through delegated classes.
 * Effectively a list of fields, where the value of one field is instance of following field's declaring class.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Path {
   private final Field[] fields;
   private boolean complete;

   public Path(Field[] fields) {
      if (fields.length == 0) throw new IllegalArgumentException();
      this.fields = fields;
   }

   public Path(Field field) {
      this(new Field[] {field});
   }

   public Object get(Object source) throws IllegalAccessException {
      for (Field f : fields) {
         f.setAccessible(true);
         source = f.get(source);
      }
      return source;
   }

   public void set(Object source, Object value) throws IllegalAccessException {
      for (int i = 0; i < fields.length - 1; ++i) {
         Field f = fields[i];
         f.setAccessible(true);
         source = f.get(source);
      }
      Field f = fields[fields.length - 1];
      f.setAccessible(true);
      f.set(source, value);
   }

   public Path with(Field field) {
      Field[] newFields = Arrays.copyOf(fields, fields.length + 1);
      newFields[fields.length] = field;
      return new Path(newFields);
   }

   public Property getTargetAnnotation() {
      return fields[fields.length - 1].getAnnotation(Property.class);
   }

   public Type getTargetGenericType() {
      return fields[fields.length - 1].getGenericType();
   }

   public Class<?> getTargetType() {
      return fields[fields.length - 1].getType();
   }

   public boolean isTrivial() {
      return fields.length == 1;
   }

   public void setComplete(boolean complete) {
      this.complete = complete;
   }

   public boolean isComplete() {
      return complete;
   }

   public <T extends Annotation> boolean isAnnotationPresent(Class<T> annotationClass) {
      for (Field f : fields) {
         if (f.isAnnotationPresent(annotationClass)) {
            return true;
         }
      }
      return false;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < fields.length; ++i) {
         if (i != 0) sb.append(" -> ");
         sb.append(fields[i].getDeclaringClass().getSimpleName()).append('.').append(fields[i].getName());
      }
      return sb.toString();
   }
}
