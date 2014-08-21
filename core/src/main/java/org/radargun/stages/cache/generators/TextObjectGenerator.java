package org.radargun.stages.cache.generators;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.radargun.config.Init;
import org.radargun.config.Property;

/**
 * Generates text objects. TextObject (by default it is org.radargun.query.TextObject)
 * should have constructor and method with these signatures:
 *
 * {@code}
 * public class TextObject {
 *    public TextObject(String text) { ... }
 *    public String getText() { ... }
 * }
 * {@code}
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class TextObjectGenerator implements ValueGenerator {

   @Property(name = "class", doc = "Class instantiated by this generator. Default is 'org.radargun.query.TextObject'")
   private String clazz = "org.radargun.query.TextObject";

   private Constructor<?> ctor;
   private Method getText;

   @Init
   public void initClass() {
      try {
         Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(this.clazz);
         ctor = clazz.getConstructor(String.class);
         getText = clazz.getMethod("getText");
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public int sizeOf(Object value) {
      return getText(value).length();
   }

   @Override
   public boolean checkValue(Object value, int expectedSize) {
      String text = getText(value);
      return text != null && text.length() == expectedSize;
   }

   protected Object newInstance(String text) {
      try {
         return ctor.newInstance(text);
      } catch (Exception e) {
         return new IllegalStateException(e);
      }
   }

   protected String getText(Object value) {
      try {
         return (String) getText.invoke(value);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }
}
