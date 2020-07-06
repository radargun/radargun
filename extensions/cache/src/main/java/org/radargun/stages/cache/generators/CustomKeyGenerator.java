package org.radargun.stages.cache.generators;

import java.lang.reflect.Constructor;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "custom", doc = "Creates keys of specified class, using single long arg constructor.")
public class CustomKeyGenerator implements KeyGenerator {
   protected static Log log = LogFactory.getLog(TextObjectGenerator.class);

   @Property(name = "class", doc = "Fully qualified name of the key class.", optional = false)
   private String clazzName;

   private Class<?> clazz;
   private Constructor<?> ctor;

   @Init
   public void init() {
      try {
         clazz = Class.forName(clazzName);
         ctor = clazz.getConstructor(long.class);
      } catch (Exception e) {
         // trace as this can happen on main node
         log.trace("Could not initialize generator " + this, e);
      }
   }

   @Override
   public Object generateKey(long keyIndex) {
      if (ctor == null) throw new IllegalStateException("The generator was not properly initialized");
      try {
         return ctor.newInstance(keyIndex);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public String toString() {
      return PropertyHelper.getDefinitionElementName(getClass()) + PropertyHelper.toString(this);
   }
}
