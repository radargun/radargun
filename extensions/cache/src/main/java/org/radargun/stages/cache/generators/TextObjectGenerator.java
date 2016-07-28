package org.radargun.stages.cache.generators;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

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
 */
public abstract class TextObjectGenerator implements ValueGenerator {
   protected static Log log = LogFactory.getLog(TextObjectGenerator.class);

   @Property(name = "class", doc = "Class instantiated by this generator. Default is 'org.radargun.query.TextObject'.")
   private String clazz = "org.radargun.query.TextObject";

   private Constructor<?> ctor;
   private Method getText;

   @Init
   public void initClass() {
      try {
         Class<?> clazz = Class.forName(this.clazz);
         ctor = clazz.getConstructor(String.class);
         getText = clazz.getMethod("getText");
      } catch (Exception e) {
         // trace as this can happen on master node
         log.trace("Could not initialize generator " + this, e);
      }
   }

   @Override
   public int sizeOf(Object value) {
      return getText(value).length();
   }

   @Override
   public boolean checkValue(Object value, Object key, int expectedSize) {
      String text = getText(value);
      return text != null && text.length() == expectedSize;
   }

   protected Object newInstance(String text) {
      if (ctor == null) throw new IllegalStateException("The generator was not properly initialized");
      try {
         return ctor.newInstance(text);
      } catch (Exception e) {
         return new IllegalStateException(e);
      }
   }

   protected String getText(Object value) {
      if (getText == null) throw new IllegalStateException("The generator was not properly initialized");
      try {
         return (String) getText.invoke(value);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }
}
