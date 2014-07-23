package org.radargun.stages.cache.generators;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.utils.Utils;

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

   @Override
   public void init(String param, ClassLoader classLoader) {
      PropertyHelper.setProperties(this, Utils.parseParams(param), false, false);
      try {
         Class<?> clazz = classLoader.loadClass(this.clazz);
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
