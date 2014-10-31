package org.radargun.stages.cache.generators;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "plugin-specific", doc = "Wraps key generator that is specific to current plugin")
public class PluginSpecificKeyGenerator implements KeyGenerator {
   private static final Log log = LogFactory.getLog(PluginSpecificKeyGenerator.class);

   @Property(name = "class", doc = "Fully qualified name of the key generator class.", optional = false)
   protected String clazzName;

   @Property(doc = "Parameters for the generator (used to initialize properties). By default none.")
   protected String params = null;

   @Property(doc = "Generator used when the plugin-specific generator is not available. By default an exception is thrown.", complexConverter = KeyGenerator.ComplexConverter.class)
   protected KeyGenerator fallback;

   protected KeyGenerator instance;

   @Init
   public void init() {
      try {
         instance = Utils.instantiateAndInit(Thread.currentThread().getContextClassLoader(), clazzName, params);
      } catch (Exception e) {
         log.trace("Cannot load plugin-specific generator through " + this, e);
         if (fallback != null) {
            instance = fallback;
         }
      }
   }

   @Override
   public Object generateKey(long keyIndex) {
      if (instance == null) throw new IllegalStateException("The plugin specific generator was not set!");
      return instance.generateKey(keyIndex);
   }

   @Override
   public String toString() {
      return PropertyHelper.getDefinitionElementName(getClass()) + PropertyHelper.toString(this);
   }
}
