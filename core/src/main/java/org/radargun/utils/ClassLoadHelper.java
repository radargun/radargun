package org.radargun.utils;

import java.net.URLClassLoader;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.state.StateBase;

public class ClassLoadHelper {

   private static final String PREVIOUS_PLUGIN = "__PREVIOUS_PLUGIN__";
   private static final String CLASS_LOADER = "__CLASS_LOADER__";
   private static final Log log = LogFactory.getLog(ClassLoadHelper.class);
   
   private boolean useSmartClassLoading;
   private Class<?> instantiator;
   private String plugin;
   private StateBase state;

   public ClassLoadHelper(boolean useSmartClassLoading, Class<?> instantiator, String plugin, StateBase state) {
      this.useSmartClassLoading = useSmartClassLoading;
      this.instantiator = instantiator;
      this.plugin = plugin;
      this.state = state;
   }

   public Object createInstance(String classFqn) throws Exception {
      if (!useSmartClassLoading) {
         return Class.forName(classFqn).newInstance();
      }
      ClassLoader classLoader = getLoader();
      log.info("Creating newInstance " + classFqn + " with classloader " + classLoader);
      return classLoader.loadClass(classFqn).newInstance();
   }

   public ClassLoader getLoader() {
      String prevProduct = (String) state.get(PREVIOUS_PLUGIN);
      if (prevProduct == null || !prevProduct.equals(plugin)) {
         URLClassLoader classLoader = Utils.buildPluginSpecificClassLoader(plugin, instantiator.getClassLoader());
         state.put(CLASS_LOADER, classLoader);
         state.put(PREVIOUS_PLUGIN, plugin);
         return classLoader;
      } else {//same product and there is a class loader
         return (URLClassLoader) state.get(CLASS_LOADER);
      }
   }
}
